package com.scsabot.discordbot.service;

import com.scsabot.discordbot.common.exception.BusinessException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.ScheduledEvent;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private static final long UPCOMING_WINDOW_SECONDS = 604800;
    private static final long PAST_GRACE_SECONDS = 3600;

    private final ObjectProvider<JDA> jdaProvider;

    public EventService(ObjectProvider<JDA> jdaProvider) {
        this.jdaProvider = jdaProvider;
    }

    public ScheduledEvent createEvent(Guild guild, String title, String description, Instant eventTime,
                                      String eventChannelId) {
        if (guild == null) {
            throw new BusinessException("Guild is required to create an event.");
        }
        if (title == null || title.isBlank()) {
            throw new BusinessException("Event title is required.");
        }
        if (eventTime == null) {
            throw new BusinessException("Event time is required.");
        }
        if (eventTime.isBefore(Instant.now())) {
            throw new BusinessException("Event time cannot be in the past.");
        }
        if (eventChannelId == null || eventChannelId.isBlank()) {
            throw new BusinessException("Event channel ID is required.");
        }

        GuildChannel channel = guild.getGuildChannelById(eventChannelId);
        if (channel == null) {
            throw new BusinessException("Voice or stage channel not found: " + eventChannelId);
        }

        OffsetDateTime startTime = eventTime.atOffset(ZoneOffset.UTC);
        var action = guild.createScheduledEvent(title, channel, startTime);
        if (description != null && !description.isBlank()) {
            action.setDescription(description);
        }

        ScheduledEvent created = action.complete();
        log.info("Created Discord scheduled event: {} (ID: {}) in guild {}", title, created.getId(), guild.getId());
        return created;
    }

    public List<ScheduledEvent> getUpcomingEvents(Guild guild) {
        Instant now = Instant.now();
        Instant endTime = now.plusSeconds(UPCOMING_WINDOW_SECONDS);
        return retrieveEvents(guild).stream()
                .filter(this::isUsable)
                .filter(event -> {
                    Instant start = event.getStartTime().toInstant();
                    return !start.isBefore(now) && start.isBefore(endTime);
                })
                .sorted(Comparator.comparing(ScheduledEvent::getStartTime))
                .toList();
    }

    public List<ScheduledEvent> getAllFutureEvents() {
        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            return List.of();
        }

        Instant cutoff = Instant.now().minusSeconds(PAST_GRACE_SECONDS);
        return jda.getGuilds().stream()
                .flatMap(guild -> retrieveEvents(guild).stream())
                .filter(this::isUsable)
                .filter(event -> event.getStartTime().toInstant().isAfter(cutoff))
                .sorted(Comparator.comparing(ScheduledEvent::getStartTime))
                .toList();
    }

    private List<ScheduledEvent> retrieveEvents(Guild guild) {
        if (guild == null) {
            return List.of();
        }
        return guild.retrieveScheduledEvents().complete();
    }

    private boolean isUsable(ScheduledEvent event) {
        if (event == null || event.getStartTime() == null) {
            return false;
        }
        ScheduledEvent.Status status = event.getStatus();
        return status != ScheduledEvent.Status.CANCELED && status != ScheduledEvent.Status.COMPLETED;
    }
}
