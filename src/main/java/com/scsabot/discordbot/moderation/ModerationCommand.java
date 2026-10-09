package com.scsabot.discordbot.moderation;


import com.scsabot.discordbot.service.RoleService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class ModerationCommand {
    private final RoleService roleService;

    // In-memory warning counter (key = guildId:userId). Use a database for persistence.
    private final ConcurrentHashMap<String, AtomicInteger> warnings = new ConcurrentHashMap<>();

    /**
     * Returns true if the command may proceed; otherwise replies and returns false.
     */
    private boolean requireAdmin(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (event.getGuild() == null || member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ Only administrators can use this command.").setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    public void handleWarn(SlashCommandInteractionEvent event) {
        if (!requireAdmin(event)) return;

        Guild guild = event.getGuild();
        User target = event.getOption("user").getAsUser();
        Member targetMember = event.getOption("user").getAsMember();
        String reason = event.getOption("reason") != null
                ? event.getOption("reason").getAsString() : "No reason provided";

        // Check BEFORE granting the role
        boolean warnedBefore = hasWarnedRole(guild, targetMember);

        roleService.grantRoles(targetMember, "warned");

        String dm;
        if (warnedBefore) {
            dm = "⚠️ You have been **warned** again in **" + guild.getName() + "**\n"
                    + "Reason: " + reason + "\n\n"
                    + "You were warned before. This time, please take it seriously. "
                    + "Admins can ban you without any further notice.";
        } else {
            dm = "⚠️ You have been **warned** in **" + guild.getName() + "**\n"
                    + "Reason: " + reason;
        }

        event.deferReply().queue();

        String status = warnedBefore ? "(repeat warning)" : "(first warning)";

        target.openPrivateChannel()
                .flatMap(channel -> channel.sendMessage(dm))
                .queue(
                        ok -> event.getHook().sendMessage(
                                "✅ Warned " + target.getAsMention() + " " + status + ". DM sent.").queue(),
                        err -> event.getHook().sendMessage(
                                "✅ Warned " + target.getAsMention() + " " + status
                                        + ", but I couldn't DM them (DMs closed).").queue()
                );
    }

    public void handleBan(SlashCommandInteractionEvent event) {
        if (!requireAdmin(event)) return;

        Guild guild = event.getGuild();
        Member moderator = event.getMember();
        User target = event.getOption("user").getAsUser();
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason provided";

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

        String dm = "🔨 You have been **banned** from **" + guild.getName() + "**\n" + "Reason: " + reason;

        event.deferReply().queue();

        // DM first; after the ban the bot may no longer share a server with the user.
        target.openPrivateChannel().flatMap(channel -> channel.sendMessage(dm)).queue(ok -> ban(event, guild, target, reason, true), err -> ban(event, guild, target, reason, false));
    }

    private void ban(SlashCommandInteractionEvent event, Guild guild, User target, String reason, boolean dmSent) {
        guild.ban(target, 0, TimeUnit.SECONDS).reason(reason).queue(ok -> event.getHook().sendMessage("🔨 Banned " + target.getAsTag() + (dmSent ? ". DM sent." : ". (Couldn't DM them.)")).queue(), err -> event.getHook().sendMessage("❌ Failed to ban: " + err.getMessage()).queue());
    }

    private boolean hasWarnedRole(Guild guild, Member member) {
        if (member == null) return false;

        String roleId = roleService.getRoleId("warned");
        if (roleId == null) return false;

        Role role = guild.getRoleById(roleId);
        return role != null && member.getRoles().contains(role);
    }
}