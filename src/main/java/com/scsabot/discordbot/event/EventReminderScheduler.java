package com.scsabot.discordbot.event;

import com.scsabot.discordbot.service.EventPostService;
import com.scsabot.discordbot.service.EventService;
import net.dv8tion.jda.api.entities.ScheduledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EventReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventReminderScheduler.class);

    private final EventService eventService;
    private final EventPostService eventPostService;
    private final Set<String> sentReminders = ConcurrentHashMap.newKeySet();

    public EventReminderScheduler(EventService eventService, EventPostService eventPostService) {
        this.eventService = eventService;
        this.eventPostService = eventPostService;
    }

    @Scheduled(fixedRate = 30000)
    public void checkAndSendReminders() {
        log.debug("Checking for due event reminders...");

        List<ScheduledEvent> futureEvents = eventService.getAllFutureEvents();

        for (ScheduledEvent event : futureEvents) {
            Instant now = Instant.now();
            long secondsUntilEvent = event.getStartTime().toInstant().getEpochSecond() - now.getEpochSecond();

            log.debug("Event '{}' (ID: {}) - seconds until event: {}", event.getName(), event.getId(), secondsUntilEvent);

            if (ReminderWindows.isDue(secondsUntilEvent, ReminderWindows.REMINDER_24H)) {
                sendReminderOnce(event, "24h");
            }
            if (ReminderWindows.isDue(secondsUntilEvent, ReminderWindows.REMINDER_1H)) {
                sendReminderOnce(event, "1h");
            }
            if (ReminderWindows.isDue(secondsUntilEvent, ReminderWindows.REMINDER_15M)) {
                sendReminderOnce(event, "15m");
            }
            if (ReminderWindows.isDue(secondsUntilEvent, ReminderWindows.REMINDER_START)) {
                sendReminderOnce(event, "start");
            }
        }
    }

    private void sendReminderOnce(ScheduledEvent event, String reminderType) {
        String key = event.getId() + ":" + reminderType;
        if (!sentReminders.add(key)) {
            return;
        }
        try {
            log.info("Sending {} reminder for event '{}' (ID: {})", reminderType, event.getName(), event.getId());
            eventPostService.postReminder(event, reminderType);
        } catch (Exception e) {
            sentReminders.remove(key);
            log.error("Failed to send {} reminder for event ID {}: {}", reminderType, event.getId(), e.getMessage(), e);
        }
    }
}
