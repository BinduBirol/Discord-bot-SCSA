package com.scsabot.discordbot.roles.interest;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.scsabot.discordbot.roles.interest.Interests.Interest;


import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

/**
 * Creates interest roles on demand. Unlike countries, a member can hold any number of
 * interest roles, so assigning one never removes another. Role name format: "🎵 Music".
 */
@Component
public class InterestRoleService {

    private static final Logger log = LoggerFactory.getLogger(InterestRoleService.class);

    /**
     * In-flight role creations, so two simultaneous reactions don't create duplicate roles.
     */
    private final Map<String, CompletableFuture<Role>> pending = new ConcurrentHashMap<>();

    public void assignInterestRole(Guild guild, Member member, Interests.Interest interest) {
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            log.warn("Missing MANAGE_ROLES permission in guild {}", guild.getId());
            return;
        }

        getOrCreateRole(guild, interest).thenAccept(role -> {
            if (!guild.getSelfMember().canInteract(role)) {
                log.warn("Cannot manage role '{}' in guild {} (move the bot's role higher)",
                        role.getName(), guild.getId());
                return;
            }
            if (member.getRoles().contains(role)) {
                return;
            }
            guild.addRoleToMember(member, role)
                    .queue(null, err -> log.error("Failed to add role to {}", member.getId(), err));
        }).exceptionally(err -> {
            log.error("Failed to get or create role {}", interest.roleName(), err);
            return null;
        });
    }

    public void removeInterestRole(Guild guild, Member member, Interests.Interest interest) {
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            return;
        }
        for (Role role : member.getRoles()) {
            if (role.getName().equals(interest.roleName()) && guild.getSelfMember().canInteract(role)) {
                guild.removeRoleFromMember(member, role)
                        .queue(null, err -> log.error("Failed to remove role from {}", member.getId(), err));
                return;
            }
        }
    }

    private CompletableFuture<Role> getOrCreateRole(Guild guild, Interest interest) {
        String name = interest.roleName();
        var existing = guild.getRolesByName(name, false);
        if (!existing.isEmpty()) {
            return CompletableFuture.completedFuture(existing.get(0));
        }
        String key = guild.getId() + ":" + interest.code();
        CompletableFuture<Role> future = pending.computeIfAbsent(key,
                k -> guild.createRole().setName(name).setMentionable(false).submit());
        future.whenComplete((r, t) -> pending.remove(key, future));
        return future;
    }
}
