package com.scsabot.discordbot.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "event", indexes = {
        @Index(name = "idx_event_guild_id", columnList = "guild_id"),
        @Index(name = "idx_event_event_time", columnList = "event_time"),
        @Index(name = "idx_event_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(name = "event_channel_id", nullable = false)
    private String eventChannelId;

    @Column(name = "post_channel_id", nullable = false)
    private String postChannelId;

    @Column(name = "post_message_id")
    private String postMessageId;

    @Column(name = "eventLink", unique = true, nullable = false)
    private String eventLink;

    @Column(name = "reminder_24h_sent", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean reminder24hSent = false;

    @Column(name = "reminder_1h_sent", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean reminder1hSent = false;

    @Column(name = "reminder_15m_sent", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean reminder15mSent = false;

    @Column(name = "reminder_start_sent", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean reminderStartSent = false;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Event(String guildId, String title, String description, Instant eventTime,
                 String eventChannelId, String postChannelId, String createdBy, String eventLink) {
        this.guildId = guildId;
        this.title = title;
        this.description = description;
        this.eventTime = eventTime;
        this.eventChannelId = eventChannelId;
        this.postChannelId = postChannelId;
        this.createdBy = createdBy;
        this.eventLink = eventLink;
        this.createdAt = Instant.now();
    }
}
