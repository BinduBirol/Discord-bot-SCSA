package com.scsabot.discordbot.roles;


import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

/**
 * Creates country roles on demand and enforces "one country role per member".
 * Role name format: "🇧🇩 Bangladesh".
 */
@Component
public class CountryRoleService {

    private static final Logger log = LoggerFactory.getLogger(CountryRoleService.class);

    /**
     * In-flight role creations, so two simultaneous reactions don't create duplicate roles.
     */
    private final Map<String, CompletableFuture<Role>> pending = new ConcurrentHashMap<>();

    public void assignCountryRole(Guild guild, Member member, CountryFlags.Country country) {
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            log.warn("Missing MANAGE_ROLES permission in guild {}", guild.getId());
            return;
        }

        getOrCreateRole(guild, country).thenAccept(role -> {
            if (!guild.getSelfMember().canInteract(role)) {
                log.warn("Cannot manage role '{}' in guild {} (move the bot's role higher)",
                        role.getName(), guild.getId());
                return;
            }
            List<Role> toRemove = member.getRoles().stream()
                    .filter(r -> !r.equals(role))
                    .filter(r -> CountryFlags.fromRoleName(r.getName()).isPresent())
                    .filter(r -> guild.getSelfMember().canInteract(r))
                    .toList();
            if (member.getRoles().contains(role) && toRemove.isEmpty()) {
                return;
            }
            guild.modifyMemberRoles(member, List.of(role), toRemove)
                    .queue(null, err -> log.error("Failed to update roles for {}", member.getId(), err));
        }).exceptionally(err -> {
            log.error("Failed to get or create role {}", country.roleName(), err);
            return null;
        });
    }

    public void removeCountryRole(Guild guild, Member member, CountryFlags.Country country) {
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            return;
        }
        for (Role role : member.getRoles()) {
            if (role.getName().equals(country.roleName()) && guild.getSelfMember().canInteract(role)) {
                guild.removeRoleFromMember(member, role)
                        .queue(null, err -> log.error("Failed to remove role from {}", member.getId(), err));
                return;
            }
        }
    }

    private CompletableFuture<Role> getOrCreateRole(Guild guild, CountryFlags.Country country) {
        String name = country.roleName();
        List<Role> existing = guild.getRolesByName(name, false);
        if (!existing.isEmpty()) {
            return CompletableFuture.completedFuture(existing.get(0));
        }
        String key = guild.getId() + ":" + country.code();
        CompletableFuture<Role> future = pending.computeIfAbsent(key,
                k -> guild.createRole().setName(name).setMentionable(false).submit());
        future.whenComplete((r, t) -> pending.remove(key, future));
        return future;
    }
}