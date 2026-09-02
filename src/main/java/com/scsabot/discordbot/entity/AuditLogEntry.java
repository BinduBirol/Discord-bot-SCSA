package com.scsabot.discordbot.entity;

import com.scsabot.discordbot.common.audit.AuditAction;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_log_entry", indexes = {
        @Index(name = "idx_audit_log_entry_entity_id", columnList = "entity_id"),
        @Index(name = "idx_audit_log_entry_created_at", columnList = "created_at")
})
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "entity_id")
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AuditAction action;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLogEntry() {
    }

    public AuditLogEntry(String guildId, String entityId, AuditAction action, String message) {
        this.guildId = guildId;
        this.entityId = entityId;
        this.action = action;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getEntityId() {
        return entityId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
