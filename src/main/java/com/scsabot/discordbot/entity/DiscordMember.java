package com.scsabot.discordbot.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "discord_member", indexes = {
        @Index(name = "idx_discord_member_discord_user_id", columnList = "discord_user_id", unique = true),
        @Index(name = "idx_discord_member_guild_id", columnList = "guild_id")
})
public class DiscordMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "discord_user_id", nullable = false, unique = true)
    private String discordUserId;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "username")
    private String username;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "country")
    private String country;

    @Column(name = "region")
    private String region;

    @Column(name = "stuttering_level")
    private String stutteringLevel;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected DiscordMember() {
    }

    public DiscordMember(String discordUserId, String guildId, String username, String displayName, Instant joinedAt) {
        this.discordUserId = discordUserId;
        this.guildId = guildId;
        this.username = username;
        this.displayName = displayName;
        this.joinedAt = joinedAt;
        this.updatedAt = joinedAt;
        this.active = true;
    }

    @PrePersist
    @PreUpdate
    public void touch() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (joinedAt == null) {
            joinedAt = updatedAt;
        }
    }

    public Long getId() {
        return id;
    }

    public String getDiscordUserId() {
        return discordUserId;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCountry() {
        return country;
    }

    public String getRegion() {
        return region;
    }

    public String getStutteringLevel() {
        return stutteringLevel;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setUsername(String username) {
        this.username = username;
        this.updatedAt = Instant.now();
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.updatedAt = Instant.now();
    }

    public void setCountry(String country) {
        this.country = country;
        this.updatedAt = Instant.now();
    }

    public void setRegion(String region) {
        this.region = region;
        this.updatedAt = Instant.now();
    }

    public void setStutteringLevel(String stutteringLevel) {
        this.stutteringLevel = stutteringLevel;
        this.updatedAt = Instant.now();
    }

    public void setActive(boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }
}
