package com.scsabot.discordbot.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReminderWindowsTest {

    @Test
    void startReminderIsDueNearZero() {
        assertTrue(ReminderWindows.isDue(0, ReminderWindows.REMINDER_START));
        assertTrue(ReminderWindows.isDue(30, ReminderWindows.REMINDER_START));
        assertTrue(ReminderWindows.isDue(-30, ReminderWindows.REMINDER_START));
        assertFalse(ReminderWindows.isDue(31, ReminderWindows.REMINDER_START));
        assertFalse(ReminderWindows.isDue(-31, ReminderWindows.REMINDER_START));
    }

    @Test
    void hourReminderUsesWindowAroundTarget() {
        assertTrue(ReminderWindows.isDue(ReminderWindows.REMINDER_1H, ReminderWindows.REMINDER_1H));
        assertTrue(ReminderWindows.isDue(ReminderWindows.REMINDER_1H - 60, ReminderWindows.REMINDER_1H));
        assertTrue(ReminderWindows.isDue(ReminderWindows.REMINDER_1H + 30, ReminderWindows.REMINDER_1H));
        assertFalse(ReminderWindows.isDue(ReminderWindows.REMINDER_1H - 61, ReminderWindows.REMINDER_1H));
        assertFalse(ReminderWindows.isDue(ReminderWindows.REMINDER_1H + 31, ReminderWindows.REMINDER_1H));
    }
}
