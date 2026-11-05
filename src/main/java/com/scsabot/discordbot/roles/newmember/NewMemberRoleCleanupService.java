package com.scsabot.discordbot.roles.newmember;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Every 3 days at 12:01 AM BDT, removes the "new member" role from members
 * who have been in the server for at least one month, and posts a summary
 * to the activity log channel.
 */
@Service
public class NewMemberRoleCleanupService {

    private static final Logger log = LoggerFactory.getLogger(NewMemberRoleCleanupService.class);

    private static final ZoneId BDT = ZoneId.of("Asia/Dhaka");
    private static final int INTERVAL_DAYS = 1;

    private final ObjectProvider<JDA> jdaProvider;
    private final NewMemberRoleLogger activityLogger;
    private final String guildId;
    private final String newMemberRoleId;

    public NewMemberRoleCleanupService(
            ObjectProvider<JDA> jdaProvider,
            NewMemberRoleLogger activityLogger,
            @Value("${discord.guild-id:}") String guildId,
            @Value("${bot.roles.new-member}") String newMemberRoleId) {
        this.jdaProvider = jdaProvider;
        this.activityLogger = activityLogger;
        this.guildId = guildId;
        this.newMemberRoleId = newMemberRoleId;
    }

    /**
     * Fires daily at 00:01 BDT, but only proceeds every 3rd day. Using the epoch day
     * keeps the 3-day rhythm stable across restarts and month boundaries
     * (a cron like "* * * *\/3" would reset at the end of each month).
     */
    @Scheduled(cron = "0 1 0 * * *", zone = "Asia/Dhaka")
    public void scheduledRun() {
        long epochDay = LocalDate.now(BDT).toEpochDay();
        if (epochDay % INTERVAL_DAYS != 0) {
            return;
        }
        runCleanup();
    }

    /** Can also be called manually (e.g. from an admin slash command) for testing. */
    public void runCleanup() {
        JDA jda = jdaProvider.getObject();

        Guild guild = guildId.isBlank()
                ? jda.getGuilds().stream().findFirst().orElse(null)
                : jda.getGuildById(guildId);

        if (guild == null) {
            log.warn("New member role cleanup skipped: guild not found");
            return;
        }

        Role role = guild.getRoleById(newMemberRoleId);
        if (role == null) {
            log.warn("New member role cleanup skipped: role {} not found", newMemberRoleId);
            return;
        }

        guild.findMembersWithRoles(role)
                .onSuccess(members -> processMembers(guild, role, members))
                .onError(error -> log.error("Failed to load members with new member role", error));
    }

    private void processMembers(Guild guild, Role role, List<Member> members) {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMonths(1);

        List<Member> eligible = members.stream()
                .filter(member -> !member.getUser().isBot())
                .filter(member -> !member.getTimeJoined().isAfter(cutoff))
                .toList();

        log.info("New member role cleanup: {} with role, {} eligible for removal",
                members.size(), eligible.size());

        if (eligible.isEmpty()) {
            return;
        }

        List<Member> removed = new CopyOnWriteArrayList<>();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger remaining = new AtomicInteger(eligible.size());

        for (Member member : eligible) {
            guild.removeRoleFromMember(member, role)
                    .reason("New member period (1 month) completed")
                    .queue(
                            success -> {
                                removed.add(member);
                                finishIfDone(role, removed, failed, remaining);
                            },
                            error -> {
                                log.warn("Could not remove role from {}: {}",
                                        member.getId(), error.getMessage());
                                failed.incrementAndGet();
                                finishIfDone(role, removed, failed, remaining);
                            });
        }
    }

    private void finishIfDone(
            Role role,
            List<Member> removed,
            AtomicInteger failed,
            AtomicInteger remaining) {

        if (remaining.decrementAndGet() == 0) {
            activityLogger.logRoleCleanup(role.getId(), removed, failed.get());
        }
    }
}
