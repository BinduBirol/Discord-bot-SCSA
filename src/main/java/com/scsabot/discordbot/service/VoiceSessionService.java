package com.scsabot.discordbot.service;

import com.scsabot.discordbot.entity.DiscordMember;
import com.scsabot.discordbot.entity.VoiceSession;
import com.scsabot.discordbot.repository.VoiceSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class VoiceSessionService {

    private static final Logger log = LoggerFactory.getLogger(VoiceSessionService.class);

    private final VoiceSessionRepository voiceSessionRepository;

    public VoiceSessionService(VoiceSessionRepository voiceSessionRepository) {
        this.voiceSessionRepository = voiceSessionRepository;
    }

    @Transactional
    public VoiceSession startSession(DiscordMember member, String guildId, String channelId) {
        Optional<VoiceSession> activeSession = voiceSessionRepository.findFirstByMemberIdAndLeftAtIsNull(member.getId());
        if (activeSession.isPresent()) {
            log.debug("Member {} already has an active voice session; closing it before starting a new one.", member.getDiscordUserId());
            VoiceSession current = activeSession.get();
            current.close(Instant.now());
            voiceSessionRepository.save(current);
        }

        VoiceSession session = new VoiceSession(member, guildId, channelId, Instant.now());
        log.info("Starting voice session for member {} in channel {}", member.getDiscordUserId(), channelId);
        return voiceSessionRepository.save(session);
    }

    @Transactional
    public Optional<VoiceSession> endSession(DiscordMember member) {
        Optional<VoiceSession> activeSession = voiceSessionRepository.findFirstByMemberIdAndLeftAtIsNull(member.getId());
        if (activeSession.isEmpty()) {
            return Optional.empty();
        }

        VoiceSession session = activeSession.get();
        Instant now = Instant.now();
        session.close(now);
        log.info("Ended voice session for member {} in channel {} after {} seconds",
                member.getDiscordUserId(), session.getVoiceChannelId(), session.getDurationSeconds());
        return Optional.of(voiceSessionRepository.save(session));
    }
}
