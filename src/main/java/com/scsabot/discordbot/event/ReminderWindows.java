package com.scsabot.discordbot.event;

public final class ReminderWindows {

    public static final long REMINDER_24H = 24 * 60 * 60;
    public static final long REMINDER_1H = 60 * 60;
    public static final long REMINDER_15M = 15 * 60;
    public static final long REMINDER_START = 0;

    private ReminderWindows() {
    }

    public static boolean isDue(long secondsUntilEvent, long reminderWindow) {
        if (reminderWindow == 0) {
            return secondsUntilEvent >= -30 && secondsUntilEvent <= 30;
        }
        return secondsUntilEvent >= reminderWindow - 60 && secondsUntilEvent <= reminderWindow + 30;
    }
}
