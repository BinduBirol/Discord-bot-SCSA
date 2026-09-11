package com.scsabot.discordbot.service;

import com.scsabot.discordbot.common.exception.BusinessException;
import com.scsabot.discordbot.entity.Event;
import com.scsabot.discordbot.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import java.util.List;

@Service
public class EventService {

    private static final Logger log = LoggerFactory.getLogger(EventService.class);
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Dhaka");

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public Event createEvent(String guildId, String title, String description, Instant eventTime,
                            String eventChannelId, String postChannelId, String createdBy) {
        if (guildId == null || guildId.isBlank() || title == null || title.isBlank()) {
            throw new BusinessException("Guild ID and event title are required.");
        }
        if (eventTime == null) {
            throw new BusinessException("Event time is required.");
        }
        if (eventTime.isBefore(Instant.now())) {
            throw new BusinessException("Event time cannot be in the past.");
        }

        Event event = new Event(guildId, title, description, eventTime, eventChannelId, postChannelId, createdBy);
        Event saved = eventRepository.save(event);
        log.info("Created event: {} (ID: {}) in guild {}", title, saved.getId(), guildId);
        return saved;
    }

    @Transactional
    public Event updateEvent(Long eventId, String guildId, Event updateData) {
        Event event = eventRepository.findByIdAndGuildId(eventId, guildId)
                .orElseThrow(() -> new BusinessException("Event not found for ID: " + eventId + " in guild: " + guildId));

        if (updateData.getTitle() != null && !updateData.getTitle().isBlank()) {
            event.setTitle(updateData.getTitle());
        }
        if (updateData.getDescription() != null) {
            event.setDescription(updateData.getDescription());
        }
        if (updateData.getPostMessageId() != null && !updateData.getPostMessageId().isBlank()) {
            event.setPostMessageId(updateData.getPostMessageId());
        }

        return eventRepository.save(event);
    }

    @Transactional
    public void updateReminderStatus(Long eventId, String reminderType, boolean sent) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException("Event not found for ID: " + eventId));

        switch (reminderType) {
            case "24h" -> event.setReminder24hSent(sent);
            case "1h" -> event.setReminder1hSent(sent);
            case "15m" -> event.setReminder15mSent(sent);
            case "start" -> event.setReminderStartSent(sent);
            default -> throw new BusinessException("Unknown reminder type: " + reminderType);
        }

        eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public Event getEvent(Long eventId, String guildId) {
        return eventRepository.findByIdAndGuildId(eventId, guildId)
                .orElseThrow(() -> new BusinessException("Event not found for ID: " + eventId + " in guild: " + guildId));
    }

    @Transactional(readOnly = true)
    public List<Event> getUpcomingEvents(String guildId) {
        Instant now = Instant.now();
        Instant endTime = now.plusSeconds(604800); // 7 days from now
        return eventRepository.findUpcomingEvents(guildId, now, endTime);
    }

    @Transactional(readOnly = true)
    public List<Event> getAllFutureEvents() {
        return eventRepository.findFutureEvents(Instant.now());
    }

    @Transactional
    public void deleteEvent(Long eventId, String guildId) {
        Event event = eventRepository.findByIdAndGuildId(eventId, guildId)
                .orElseThrow(() -> new BusinessException("Event not found for ID: " + eventId + " in guild: " + guildId));
        eventRepository.delete(event);
        log.info("Deleted event ID: {} from guild {}", eventId, guildId);
    }

    public ZonedDateTime convertToDisplayTime(Instant instant) {
        return ZonedDateTime.ofInstant(instant, DISPLAY_ZONE);
    }
}
