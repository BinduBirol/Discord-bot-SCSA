package com.scsabot.discordbot.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "voice_session", indexes = {
        @Index(name = "idx_voice_session_member_id", columnList = "member_id"),
        @Index(name = "idx_voice_session_guild_channel", columnList = "guild_id, voice_channel_id")
})
public class VoiceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private DiscordMember member;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "voice_channel_id", nullable = false)
    private String voiceChannelId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    protected VoiceSession() {
    }

    public VoiceSession(DiscordMember member, String guildId, String voiceChannelId, Instant joinedAt) {
        this.member = member;
        this.guildId = guildId;
        this.voiceChannelId = voiceChannelId;
        this.joinedAt = joinedAt;
    }

    public void close(Instant leftAt) {
        this.leftAt = leftAt;
        if (joinedAt != null) {
            this.durationSeconds = java.time.Duration.between(joinedAt, leftAt).getSeconds();
        }
    }

    public Long getId() {
        return id;
    }

    public DiscordMember getMember() {
        return member;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getVoiceChannelId() {
        return voiceChannelId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getLeftAt() {
        return leftAt;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }
}
