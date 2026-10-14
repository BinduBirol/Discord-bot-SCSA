package com.scsabot.discordbot.coach;

import java.awt.Color;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;

/**
 * Publishes one profile card per coach (with Check status / Get access / Remove access buttons) in the channel set
 * by discord.coaching-access-channel-id, once per guild, and handles the button clicks. All replies are ephemeral
 * (only the person who clicked sees them).
 * <p>
 * To add Coach 2 later: add the role ID + user ID to the config and one line in the constructor (see "COACHES").
 */
@Component
public class CoachingAccessListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(CoachingAccessListener.class);

    private static final String PREFIX = "coaching";
    private static final String STATUS = "status";
    private static final String GET = "get";
    private static final String REMOVE = "remove";

    /**
     * One coach: a short key (used in button IDs and storage), a label, the coach's Discord user and access role.
     */
    private record Coach(String key, String label, String userId, String roleId) {
    }

    private final CoachingAccessStore store;
    private final String channelId;
    private final List<Coach> coaches;
    private final Set<String> publishing = ConcurrentHashMap.newKeySet();

    public CoachingAccessListener(CoachingAccessStore store,
                                  @Value("${discord.coaching-access-channel-id:}") String channelId,
                                  @Value("${bot.roles.coaching-access:}") String coach1RoleId,
                                  @Value("${bot.coaching.coach1-user-id:439443867289518090}") String coach1UserId) {
        this.store = store;
        this.channelId = channelId;
        // ------------------------------------------------------------ COACHES
        this.coaches = List.of(
                new Coach("coach1", "Coach Jannis", coach1UserId, coach1RoleId)
                // , new Coach("coach2", "Coach 2", coach2UserId, coach2RoleId)
        );
    }

    // ---------------------------------------------------------------- publishing

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        if (channelId == null || channelId.isBlank()) {
            log.warn("discord.coaching-access-channel-id is not set; coaching access cards disabled");
            return;
        }
        Guild guild = event.getGuild();
        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) {
            log.warn("Channel {} (discord.coaching-access-channel-id) not found in guild {}", channelId, guild.getId());
            return;
        }
        for (Coach coach : coaches) {
            ensurePost(guild, channel, coach);
        }
    }

    private void ensurePost(Guild guild, TextChannel channel, Coach coach) {
        long guildId = guild.getIdLong();
        String lock = guildId + ":" + coach.key();
        if (!publishing.add(lock)) {
            return; // already publishing/verifying (e.g. reconnect)
        }

        var existing = store.getMessageId(guildId, coach.key());
        if (existing.isEmpty()) {
            publish(guild, channel, coach, lock);
            return;
        }

        // Card already exists: only republish if it was deleted.
        channel.retrieveMessageById(existing.get()).queue(
                m -> publishing.remove(lock),
                err -> {
                    if (err instanceof ErrorResponseException e
                            && e.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                        log.info("{} card was deleted in guild {}, republishing", coach.label(), guildId);
                        store.clear(guildId, coach.key());
                        publish(guild, channel, coach, lock);
                    } else {
                        log.error("Could not verify {} card in guild {}", coach.label(), guildId, err);
                        publishing.remove(lock);
                    }
                });
    }

    private void publish(Guild guild, TextChannel channel, Coach coach, String lock) {
        long guildId = guild.getIdLong();
        loadCard(guild, coach, embed -> channel.sendMessageEmbeds(embed)
                .setActionRow(
                        Button.secondary(id(coach, STATUS), "ℹ️ Check status"),
                        Button.success(id(coach, GET), "🔓 Get " + coach.label() + " access"),
                        Button.danger(id(coach, REMOVE), "🔒 Remove access"))
                .queue(
                        message -> {
                            store.setMessageId(guildId, coach.key(), message.getIdLong());
                            publishing.remove(lock);
                        },
                        err -> {
                            log.error("Publishing {} card failed in guild {}", coach.label(), guildId, err);
                            publishing.remove(lock);
                        }));
    }

    /**
     * Looks up the coach's server profile (falls back to plain user, then to a card without a photo).
     */
    private void loadCard(Guild guild, Coach coach, Consumer<MessageEmbed> done) {
        guild.retrieveMemberById(coach.userId()).queue(
                member -> done.accept(card(coach, member.getEffectiveName(),
                        member.getEffectiveAvatarUrl() + "?size=256",
                        member.getTimeJoined().toEpochSecond())),
                err -> guild.getJDA().retrieveUserById(coach.userId()).queue(
                        user -> done.accept(card(coach, user.getName(),
                                user.getEffectiveAvatarUrl() + "?size=256", null)),
                        err2 -> {
                            log.warn("Could not load profile of {} ({}); posting card without photo",
                                    coach.label(), coach.userId());
                            done.accept(card(coach, coach.label(), null, null));
                        }));
    }


    private static MessageEmbed card(Coach coach, String name, String avatarUrl, Long joinedEpoch) {
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new Color(0x5865F2))
                .setAuthor(coach.label() + " Access", avatarUrl, avatarUrl)
                .setTitle("🎓 Connect with " + name)
                .setDescription(
                        "Explore **" + name + "**'s resources, free tips, and coaching programs.\n\n"
                                + "**Manage your access using the buttons below:**\n\n"
                                + "ℹ️ **Check Status** — See if you have access.\n"
                                + "🔓 **Get Access** — Receive the access role.\n"
                                + "🔒 **Remove Access** — Remove the role from yourself."
                )
                .addField("Coach", "<@" + coach.userId() + ">", true)
                .addField("Access Role", "<@&" + coach.roleId() + ">", true)
                .setFooter("Only you can see the reply to your click");

        if (joinedEpoch != null) {
            eb.addField("Member Since", "<t:" + joinedEpoch + ":D>", true);
        }

        return eb.build();
    }


    private static String id(Coach coach, String action) {
        return PREFIX + ":" + coach.key() + ":" + action;
    }

    // ---------------------------------------------------------------- buttons

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        if (parts.length != 3 || !parts[0].equals(PREFIX)) {
            return;
        }
        Coach coach = coaches.stream().filter(c -> c.key().equals(parts[1])).findFirst().orElse(null);
        String action = parts[2];
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (coach == null) {
            return;
        }
        if (guild == null || member == null) {
            event.reply("This only works inside the server.").setEphemeral(true).queue();
            return;
        }
        if (!store.isTracked(guild.getIdLong(), coach.key(), event.getMessageIdLong())) {
            return;
        }

        Role role = (coach.roleId() == null || coach.roleId().isBlank()) ? null : guild.getRoleById(coach.roleId());
        if (role == null) {
            log.warn("Access role {} for {} not found in guild {}", coach.roleId(), coach.label(), guild.getId());
            event.reply("⚠️ The " + coach.label() + " access role isn't configured correctly. Please tell a moderator.")
                    .setEphemeral(true).queue();
            return;
        }

        boolean hasRole = member.getRoles().contains(role);
        String label = coach.label();

        switch (action) {
            case STATUS -> event.reply(hasRole
                            ? "✅ You **have** " + label + " access."
                            : "❌ You **don't have** " + label + " access yet. Press **Get " + label + " access** to add it.")
                    .setEphemeral(true).queue();
            case GET -> {
                if (hasRole) {
                    event.reply("✅ You already have " + label + " access.").setEphemeral(true).queue();
                } else if (canManage(guild, role, event)) {
                    event.deferReply(true).queue();
                    guild.addRoleToMember(member, role).queue(
                            v -> event.getHook().editOriginal("✅ Done, you now have " + label + " access.").queue(),
                            err -> fail(event, "add", member, err));
                }
            }
            case REMOVE -> {
                if (!hasRole) {
                    event.reply("ℹ️ You don't have " + label + " access, so there is nothing to remove.")
                            .setEphemeral(true).queue();
                } else if (canManage(guild, role, event)) {
                    event.deferReply(true).queue();
                    guild.removeRoleFromMember(member, role).queue(
                            v -> event.getHook().editOriginal("✅ Your " + label + " access has been removed.").queue(),
                            err -> fail(event, "remove", member, err));
                }
            }
            default -> {
            }
        }
    }

    /**
     * Checks the bot can actually hand out the role; replies to the user if it can't.
     */
    private boolean canManage(Guild guild, Role role, ButtonInteractionEvent event) {
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)
                || !guild.getSelfMember().canInteract(role)) {
            log.warn("Cannot manage role '{}' in guild {} (needs Manage Roles and a higher role than '{}')",
                    role.getName(), guild.getId(), role.getName());
            event.reply("⚠️ I can't change that role right now. Please tell a moderator.")
                    .setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    private void fail(ButtonInteractionEvent event, String action, Member member, Throwable err) {
        log.error("Failed to {} coaching access role for {}", action, member.getId(), err);
        event.getHook().editOriginal("⚠️ Something went wrong. Please try again or tell a moderator.").queue();
    }
}