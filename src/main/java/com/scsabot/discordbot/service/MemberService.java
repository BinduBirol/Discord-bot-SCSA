package com.scsabot.discordbot.service;

import com.scsabot.discordbot.common.exception.BusinessException;
import com.scsabot.discordbot.entity.DiscordMember;
import com.scsabot.discordbot.repository.DiscordMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private final DiscordMemberRepository memberRepository;

    public MemberService(DiscordMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional
    public DiscordMember upsertMember(String guildId, String userId, String username, String displayName) {
        if (guildId == null || userId == null || userId.isBlank()) {
            throw new BusinessException("Guild ID and user ID are required to persist a member.");
        }

        Optional<DiscordMember> existing = memberRepository.findByDiscordUserIdAndGuildId(userId, guildId);

        if (existing.isPresent()) {
            DiscordMember member = existing.get();
            member.setUsername(username);
            member.setDisplayName(displayName);
            member.setActive(true);
            log.debug("Updated member {} in guild {}", userId, guildId);
            return memberRepository.save(member);
        }

        DiscordMember member = new DiscordMember(userId, guildId, username, displayName, Instant.now());
        log.info("Persisted new member {} in guild {}", userId, guildId);
        return memberRepository.save(member);
    }

    @Transactional
    public DiscordMember deactivateMember(String guildId, String userId) {
        DiscordMember member = memberRepository.findByDiscordUserIdAndGuildId(userId, guildId)
                .orElseThrow(() -> new BusinessException("Member not found for guild " + guildId + " and user " + userId));
        member.setActive(false);
        return memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public Optional<DiscordMember> findByDiscordUserIdAndGuildId(String guildId, String userId) {
        return memberRepository.findByDiscordUserIdAndGuildId(userId, guildId);
    }
}
