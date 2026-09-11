package com.scsabot.discordbot.event;

import com.scsabot.discordbot.entity.Event;
import com.scsabot.discordbot.service.EventPostService;
import com.scsabot.discordbot.service.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class EventReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(EventReminderScheduler.class);

    // Reminder windows (in seconds before event)
    private static final long REMINDER_24H = 24 * 60 * 60;      // 86400 seconds
    private static final long REMINDER_1H = 60 * 60;            // 3600 seconds
    private static final long REMINDER_15M = 15 * 60;           // 900 seconds
    private static final long REMINDER_START = 0;               // At event start

    private final EventService eventService;
    private final EventPostService eventPostService;

    public EventReminderScheduler(EventService eventService, EventPostService eventPostService) {
        this.eventService = eventService;
        this.eventPostService = eventPostService;
    }

    /**
     * Runs every 30 seconds to check for due reminders and send them.
     * Uses a window-based approach to avoid missing reminders during bot restarts.
     */
    @Scheduled(fixedRate = 30000) // 30 seconds
    @Transactional
    public void checkAndSendReminders() {
        log.debug("Checking for due event reminders...");

        List<Event> futureEvents = eventService.getAllFutureEvents();

        for (Event event : futureEvents) {
            Instant now = Instant.now();
            long secondsUntilEvent = (event.getEventTime().getEpochSecond() - now.getEpochSecond());

            log.debug("Event '{}' (ID: {}) - seconds until event: {}", event.getTitle(), event.getId(), secondsUntilEvent);

            // Check 24-hour reminder
            if (!event.isReminder24hSent() && isReminderDue(secondsUntilEvent, REMINDER_24H)) {
                sendReminder(event, "24h", "24h");
            }

            // Check 1-hour reminder
            if (!event.isReminder1hSent() && isReminderDue(secondsUntilEvent, REMINDER_1H)) {
                sendReminder(event, "1h", "1h");
            }

            // Check 15-minute reminder
            if (!event.isReminder15mSent() && isReminderDue(secondsUntilEvent, REMINDER_15M)) {
                sendReminder(event, "15m", "15m");
            }

            // Check event start reminder
            if (!event.isReminderStartSent() && isReminderDue(secondsUntilEvent, REMINDER_START)) {
                sendReminder(event, "start", "start");
            }

            // Skip events that are too far in the past
            if (secondsUntilEvent < -3600) { // More than 1 hour past
                log.debug("Event '{}' (ID: {}) is in the past and all reminders have been sent.", event.getTitle(), event.getId());
            }
        }
    }

    /**
     * Determines if a reminder is due based on the time until event and the reminder window.
     * Uses a window approach: reminder is due if we're within the window and haven't sent it yet.
     */
    private boolean isReminderDue(long secondsUntilEvent, long reminderWindow) {
        if (reminderWindow == 0) {
            // For event start, check if we're within 60 seconds of start time (allows for scheduler interval variations)
            return secondsUntilEvent >= -30 && secondsUntilEvent <= 30;
        }
        // For other reminders, check if we're within the window
        // Allow a 60-second window to account for scheduler runs and bot restarts
        return secondsUntilEvent >= reminderWindow - 60 && secondsUntilEvent <= reminderWindow + 30;
    }

    /**
     * Sends a reminder and updates the reminder status in the database.
     */
    private void sendReminder(Event event, String reminderType, String displayType) {
        try {
            log.info("Sending {} reminder for event '{}' (ID: {})", displayType, event.getTitle(), event.getId());
            eventPostService.postReminder(event, displayType);
            eventService.updateReminderStatus(event.getId(), reminderType, true);
        } catch (Exception e) {
            log.error("Failed to send {} reminder for event ID {}: {}", displayType, event.getId(), e.getMessage(), e);
        }
    }
}
