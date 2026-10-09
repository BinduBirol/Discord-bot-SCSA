package com.scsabot.discordbot.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ModerationListener extends ListenerAdapter {

    // Simple in-memory warning counter (key = guildId:userId). Use a database for persistence.
    private final ConcurrentHashMap<String, AtomicInteger> warnings = new ConcurrentHashMap<>();

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // Single admin check for every command
        Member member = event.getMember();
        if (event.getGuild() == null || member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ Only administrators can use this command.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        switch (event.getName()) {
            case "warn" -> handleWarn(event);
            case "ban" -> handleBan(event);
        }
    }

    private void handleWarn(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();

        User target = event.getOption("user").getAsUser();
        String reason = event.getOption("reason") != null
                ? event.getOption("reason").getAsString() : "No reason provided";

        int count = warnings
                .computeIfAbsent(guild.getId() + ":" + target.getId(), k -> new AtomicInteger())
                .incrementAndGet();

        String dm = "⚠️ You have been **warned** in **" + guild.getName() + "**\n"
                + "Reason: " + reason + "\n"
                + "Total warnings: " + count;

        event.deferReply().queue();

        target.openPrivateChannel()
                .flatMap(channel -> channel.sendMessage(dm))
                .queue(
                        ok -> event.getHook().sendMessage(
                                "✅ Warned " + target.getAsMention() + " (warning #" + count + "). DM sent.").queue(),
                        err -> event.getHook().sendMessage(
                                "✅ Warned " + target.getAsMention() + " (warning #" + count
                                        + "), but I couldn't DM them (DMs closed).").queue()
                );
    }

    private void handleBan(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member moderator = event.getMember();

        User target = event.getOption("user").getAsUser();
        String reason = event.getOption("reason") != null
                ? event.getOption("reason").getAsString() : "No reason provided";

        // Role hierarchy checks (only possible if the target is still in the server)
        Member targetMember = event.getOption("user").getAsMember();
        if (targetMember != null) {
            if (!moderator.canInteract(targetMember)) {
                event.reply("You can't ban someone with an equal or higher role.").setEphemeral(true).queue();
                return;
            }
            if (!guild.getSelfMember().canInteract(targetMember)) {
                event.reply("I can't ban that user (their role is higher than mine).").setEphemeral(true).queue();
                return;
            }
        }

        String dm = "🔨 You have been **banned** from **" + guild.getName() + "**\n"
                + "Reason: " + reason;

        event.deferReply().queue();

        // DM first, because after the ban the bot may no longer share a server with the user.
        // The ban runs whether or not the DM succeeds.
        target.openPrivateChannel()
                .flatMap(channel -> channel.sendMessage(dm))
                .queue(
                        ok -> ban(event, guild, target, reason, true),
                        err -> ban(event, guild, target, reason, false)
                );
    }

    private void ban(SlashCommandInteractionEvent event, Guild guild, User target,
                     String reason, boolean dmSent) {
        guild.ban(target, 0, TimeUnit.SECONDS)
                .reason(reason)
                .queue(
                        ok -> event.getHook().sendMessage("🔨 Banned " + target.getAsTag()
                                + (dmSent ? ". DM sent." : ". (Couldn't DM them.)")).queue(),
                        err -> event.getHook().sendMessage("❌ Failed to ban: " + err.getMessage()).queue()
                );
    }
}