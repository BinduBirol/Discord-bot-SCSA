package com.scsabot.discordbot.service;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@ConfigurationProperties(prefix = "bot")
public class RoleService {

    /**
     * Filled from bot.roles.* in application.properties (key -> role ID).
     */
    @Setter
    private Map<String, String> roles = new HashMap<>();

    /**
     * Gives the member the roles for the given keys, skipping any they
     * already have. Safe to call on every use. Does nothing in DMs.
     */
    public void grantRoles(Member member, String... keys) {

        if (member == null) {
            return;
        }

        Guild guild = member.getGuild();
        List<Role> toAdd = new ArrayList<>();

        for (String key : keys) {
            String roleId = roles.get(key);

            if (roleId == null) {
                log.warn("No role configured for key '{}'", key);
                continue;
            }

            Role role = guild.getRoleById(roleId);

            if (role == null) {
                log.warn("Role '{}' ({}) not found in guild {}", key, roleId, guild.getId());
                continue;
            }

            if (!member.getRoles().contains(role)) {
                toAdd.add(role);
            }
        }

        if (toAdd.isEmpty()) {
            return;
        }

        guild.modifyMemberRoles(member, toAdd, Collections.emptyList()).queue(
                success -> log.info("Gave roles {} to {}", toAdd, member.getId()),
                error -> log.warn("Could not give roles to {}: {}", member.getId(), error.getMessage())
        );
    }
}