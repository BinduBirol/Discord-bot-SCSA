package com.scsabot.discordbot.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "moderation_action", indexes = {
        @Index(name = "idx_moderation_action_member_id", columnList = "member_id"),
        @Index(name = "idx_moderation_action_moderator_id", columnList = "moderator_id"),
        @Index(name = "idx_moderation_action_created_at", columnList = "created_at")
})
public class ModerationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private DiscordMember member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderator_id")
    private DiscordMember moderator;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "reason")
    private String reason;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ModerationAction() {
    }

    public ModerationAction(DiscordMember member, DiscordMember moderator, String actionType, String reason, String metadata) {
        this.member = member;
        this.moderator = moderator;
        this.actionType = actionType;
        this.reason = reason;
        this.metadata = metadata;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public DiscordMember getMember() {
        return member;
    }

    public DiscordMember getModerator() {
        return moderator;
    }

    public String getActionType() {
        return actionType;
    }

    public String getReason() {
        return reason;
    }

    public String getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
