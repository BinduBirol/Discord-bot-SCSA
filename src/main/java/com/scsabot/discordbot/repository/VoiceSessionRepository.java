package com.scsabot.discordbot.repository;

import com.scsabot.discordbot.entity.VoiceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoiceSessionRepository extends JpaRepository<VoiceSession, Long> {

    Optional<VoiceSession> findFirstByMemberIdAndLeftAtIsNull(Long memberId);
}
