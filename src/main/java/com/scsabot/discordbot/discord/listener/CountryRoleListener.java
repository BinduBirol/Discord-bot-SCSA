package com.scsabot.discordbot.discord.listener;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.scsabot.discordbot.roles.CountryFlags;
import com.scsabot.discordbot.roles.CountryRoleService;
import com.scsabot.discordbot.roles.ReactionRoleStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.scsabot.discordbot.roles.CountryFlags.Country;


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
 * Publishes the country role-selection posts in the channel set by discord.get-your-role-channel-id (once per guild,
 * tracked persistently) and handles reaction add/remove.
 */
@Component
public class CountryRoleListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(CountryRoleListener.class);

    /**
     * Discord allows at most 20 distinct reactions per message.
     */
    private static final int PER_MESSAGE = 20;

    /**
     * One role-selection card: a region and up to 20 of its countries.
     */
    private record Page(String region, List<Country> chunk, int number, int total) {
    }

    private final ReactionRoleStore store;
    private final CountryRoleService roleService;
    private final String channelId;
    private final Set<Long> publishing = ConcurrentHashMap.newKeySet();

    public CountryRoleListener(ReactionRoleStore store,
                               CountryRoleService roleService,
                               @Value("${discord.get-your-role-channel-id}") String channelId) {
        this.store = store;
        this.roleService = roleService;
        this.channelId = channelId;
    }

    // ---------------------------------------------------------------- publishing

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        publishIfNeeded(event.getGuild());
    }

    private void publishIfNeeded(Guild guild) {
        long guildId = guild.getIdLong();
        if (!publishing.add(guildId)) {
            return; // already publishing/verifying (e.g. reconnect)
        }

        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            log.warn("Channel {} (discord.get-your-role-channel-id) not found in guild {}", channelId, guildId);
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
                        log.info("Role posts were deleted in guild {}, republishing", guildId);
                        deleteMessages(channel, existing);
                        store.clear(guildId);
                        publish(guild, channel);
                    } else {
                        log.error("Could not verify role posts in guild {}", guildId, err);
                        publishing.remove(guildId);
                    }
                });
    }

    private void publish(Guild guild, TextChannel channel) {
        long guildId = guild.getIdLong();
        List<Page> pages = new ArrayList<>();
        CountryFlags.byRegion().forEach((region, countries) -> {
            int total = (countries.size() + PER_MESSAGE - 1) / PER_MESSAGE;
            for (int i = 0; i < total; i++) {
                List<Country> chunk = countries.subList(i * PER_MESSAGE,
                        Math.min(countries.size(), (i + 1) * PER_MESSAGE));
                pages.add(new Page(region, chunk, i + 1, total));
            }
        });

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Page p : pages) {
            List<Country> chunk = p.chunk();
            MessageEmbed embed = buildEmbed(p);
            chain = chain
                    .thenCompose(v -> channel.sendMessageEmbeds(embed).submit())
                    .thenAccept(message -> {
                        store.addMessageId(guildId, message.getIdLong());
                        for (Country country : chunk) {
                            message.addReaction(Emoji.fromUnicode(country.flag()))
                                    .queue(null, err -> log.warn("Could not add reaction {}", country.code(), err));
                        }
                    });
        }

        chain.whenComplete((v, err) -> {
            publishing.remove(guildId);
            if (err != null) {
                log.error("Publishing role posts failed in guild {}; cleaning up so the next start retries", guildId, err);
                deleteMessages(channel, store.getMessageIds(guildId));
                store.clear(guildId);
            }
        });
    }

    /**
     * One embed "card" per page, with the countries laid out in three side-by-side columns.
     */
    private static MessageEmbed buildEmbed(Page p) {
        List<Country> chunk = p.chunk();
        String title = "🌍 Pick your country · " + p.region()
                + (p.total() > 1 ? " (" + p.number() + "/" + p.total() + ")" : "");
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new Color(0x5865F2))
                .setTitle(title)
                .setDescription("React with your flag to get your country role.")
                .setFooter("One country role per member • remove your reaction to drop the role");

        int columns = 3;
        int perColumn = (chunk.size() + columns - 1) / columns;
        for (int col = 0; col < columns; col++) {
            int from = col * perColumn;
            if (from >= chunk.size()) {
                break;
            }
            StringBuilder sb = new StringBuilder();
            for (Country c : chunk.subList(from, Math.min(chunk.size(), from + perColumn))) {
                sb.append(c.flag()).append(' ').append(c.name()).append('\n');
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
        Optional<Country> country = countryOf(event.getEmoji());
        if (country.isEmpty()) {
            return;
        }
        Guild guild = event.getGuild();
        event.retrieveMember().queue(member -> {
            if (!member.getUser().isBot()) {
                roleService.assignCountryRole(guild, member, country.get());
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
        Optional<Country> country = countryOf(event.getEmoji());
        if (country.isEmpty()) {
            return;
        }
        Guild guild = event.getGuild();
        event.retrieveMember().queue(member -> {
            if (!member.getUser().isBot()) {
                roleService.removeCountryRole(guild, member, country.get());
            }
        }, err -> log.error("Could not retrieve member {}", event.getUserId(), err));
    }

    private static Optional<Country> countryOf(EmojiUnion emoji) {
        if (emoji.getType() != Emoji.Type.UNICODE) {
            return Optional.empty();
        }
        return CountryFlags.fromFlag(emoji.getName());
    }
}