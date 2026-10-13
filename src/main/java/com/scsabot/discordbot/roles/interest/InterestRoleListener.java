package com.scsabot.discordbot.roles.interest;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.scsabot.discordbot.roles.interest.Interests.Interest;


import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;

/**
 * Publishes the interest-picker post(s) in the channel set by discord.interest-channel-id (once per guild,
 * tracked persistently) and handles reaction add/remove. Members may pick any number of interests.
 */
@Component
public class InterestRoleListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(InterestRoleListener.class);

    /**
     * Discord allows at most 20 distinct reactions per message.
     */
    private static final int PER_MESSAGE = 20;

    private final InterestRoleStore store;
    private final InterestRoleService roleService;
    private final String channelId;
    private final Set<Long> publishing = ConcurrentHashMap.newKeySet();

    public InterestRoleListener(InterestRoleStore store,
                                InterestRoleService roleService,
                                @Value("${discord.interest-channel-id:}") String channelId) {
        this.store = store;
        this.roleService = roleService;
        this.channelId = channelId;
    }

    // ---------------------------------------------------------------- publishing

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        if (channelId == null || channelId.isBlank()) {
            log.warn("discord.interest-channel-id is not set; interest picker disabled");
            return;
        }
        publishIfNeeded(event.getGuild());
    }

    private void publishIfNeeded(Guild guild) {
        long guildId = guild.getIdLong();
        if (!publishing.add(guildId)) {
            return; // already publishing/verifying (e.g. reconnect)
        }

        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            log.warn("Channel {} (discord.interest-channel-id) not found in guild {}", channelId, guildId);
            publishing.remove(guildId);
            return;
        }

        List<Long> existing = store.getMessageIds(guildId);
        if (existing.isEmpty()) {
            publish(guild, channel);
            return;
        }

        // Posts already exist: only republish if the first one was deleted.
        channel.retrieveMessageById(existing.get(0)).queue(
                m -> publishing.remove(guildId),
                err -> {
                    if (err instanceof ErrorResponseException e
                            && e.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                        log.info("Interest posts were deleted in guild {}, republishing", guildId);
                        deleteMessages(channel, existing);
                        store.clear(guildId);
                        publish(guild, channel);
                    } else {
                        log.error("Could not verify interest posts in guild {}", guildId, err);
                        publishing.remove(guildId);
                    }
                });
    }

    private void publish(Guild guild, TextChannel channel) {
        long guildId = guild.getIdLong();

        List<Interest> all = Interests.ALL;
        int total = (all.size() + PER_MESSAGE - 1) / PER_MESSAGE;
        List<List<Interest>> pages = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            pages.add(all.subList(i * PER_MESSAGE, Math.min(all.size(), (i + 1) * PER_MESSAGE)));
        }

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int i = 0; i < pages.size(); i++) {
            List<Interest> chunk = pages.get(i);
            MessageEmbed embed = buildEmbed(chunk, i + 1, total);
            chain = chain
                    .thenCompose(v -> channel.sendMessageEmbeds(embed).submit())
                    .thenCompose(message -> {
                        store.addMessageId(guildId, message.getIdLong());
                        // Add reactions one after another inside the chain so they appear in order
                        // and any failure aborts (and later retries) the whole publish.
                        CompletableFuture<Void> reactions = CompletableFuture.completedFuture(null);
                        for (Interest interest : chunk) {
                            reactions = reactions.thenCompose(
                                    x -> message.addReaction(Emoji.fromUnicode(interest.emoji())).submit());
                        }
                        return reactions;
                    });
        }

        chain.whenComplete((v, err) -> {
            publishing.remove(guildId);
            if (err != null) {
                log.error("Publishing interest posts failed in guild {}; cleaning up so the next start retries",
                        guildId, err);
                deleteMessages(channel, store.getMessageIds(guildId));
                store.clear(guildId);
            }
        });
    }

    /**
     * One embed "card" per page, with the interests laid out in three side-by-side columns.
     */
    private static MessageEmbed buildEmbed(List<Interest> chunk, int number, int total) {
        String title = "🎯 Pick your interests" + (total > 1 ? " (" + number + "/" + total + ")" : "");
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new Color(0x5865F2))
                .setTitle(title)
                .setDescription("React with an emoji to get the matching interest role.")
                .setFooter("Pick as many as you like • remove your reaction to drop the role");

        int columns = 3;
        int perColumn = (chunk.size() + columns - 1) / columns;
        for (int col = 0; col < columns; col++) {
            int from = col * perColumn;
            if (from >= chunk.size()) {
                break;
            }
            StringBuilder sb = new StringBuilder();
            for (Interest it : chunk.subList(from, Math.min(chunk.size(), from + perColumn))) {
                sb.append(it.emoji()).append(' ').append(it.name()).append('\n');
            }
            eb.addField("​", sb.toString(), true);
        }
        return eb.build();
    }

    private static void deleteMessages(TextChannel channel, List<Long> ids) {
        for (long id : ids) {
            channel.deleteMessageById(id).queue(null, e -> {
            });
        }
    }

    // ---------------------------------------------------------------- reactions

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (!event.isFromGuild() || event.getUserIdLong() == event.getJDA().getSelfUser().getIdLong()) {
            return;
        }
        if (!store.isTracked(event.getGuild().getIdLong(), event.getMessageIdLong())) {
            return;
        }
        Optional<Interest> interest = interestOf(event.getEmoji());
        if (interest.isEmpty()) {
            return;
        }
        Guild guild = event.getGuild();
        event.retrieveMember().queue(member -> {
            if (!member.getUser().isBot()) {
                roleService.assignInterestRole(guild, member, interest.get());
            }
        }, err -> log.error("Could not retrieve member {}", event.getUserId(), err));
    }

    @Override
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        if (!event.isFromGuild() || event.getUserIdLong() == event.getJDA().getSelfUser().getIdLong()) {
            return;
        }
        if (!store.isTracked(event.getGuild().getIdLong(), event.getMessageIdLong())) {
            return;
        }
        Optional<Interest> interest = interestOf(event.getEmoji());
        if (interest.isEmpty()) {
            return;
        }
        Guild guild = event.getGuild();
        event.retrieveMember().queue(member -> {
            if (!member.getUser().isBot()) {
                roleService.removeInterestRole(guild, member, interest.get());
            }
        }, err -> log.error("Could not retrieve member {}", event.getUserId(), err));
    }

    private static Optional<Interest> interestOf(EmojiUnion emoji) {
        if (emoji.getType() != Emoji.Type.UNICODE) {
            return Optional.empty();
        }
        return Interests.fromEmoji(emoji.getName());
    }
}
