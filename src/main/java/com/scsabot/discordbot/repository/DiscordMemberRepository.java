package com.scsabot.discordbot.repository;

import com.scsabot.discordbot.entity.DiscordMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiscordMemberRepository extends JpaRepository<DiscordMember, Long> {

    Optional<DiscordMember> findByDiscordUserIdAndGuildId(String discordUserId, String guildId);

    boolean existsByDiscordUserIdAndGuildId(String discordUserId, String guildId);
}
