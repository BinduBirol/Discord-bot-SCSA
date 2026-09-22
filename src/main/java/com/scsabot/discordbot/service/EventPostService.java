package com.scsabot.discordbot.service;

import com.scsabot.discordbot.common.exception.BusinessException;
import com.scsabot.discordbot.event.EventDateTimes;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.ScheduledEvent;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class EventPostService {

    private static final Logger log = LoggerFactory.getLogger(EventPostService.class);

    private static final String EVENT_UPDATE_CHANNEL_ID = "1543886877512175711";

    private final ObjectProvider<JDA> jdaProvider;

    public EventPostService(ObjectProvider<JDA> jdaProvider) {
        this.jdaProvider = jdaProvider;
    }

    public void postEventAnnouncement(ScheduledEvent event) {
        TextChannel channel = requireAnnouncementChannel();
        channel.sendMessage(formatEventText(event))
                .queue(
                        message -> log.info(
                                "Posted event announcement for event ID {} with message ID {}",
                                event.getId(),
                                message.getId()
                        ),
                        error -> log.error(
                                "Failed to post event announcement for event ID {}: {}",
                                event.getId(),
                                error.getMessage(),
                                error
                        )
                );
    }

    public void postReminder(ScheduledEvent event, String reminderType) {
        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("JDA is not initialized. Cannot post reminder.");
            return;
        }

        TextChannel channel = jda.getTextChannelById(EVENT_UPDATE_CHANNEL_ID);
        if (channel == null) {
            log.warn("Event channel not found for reminder: {}", EVENT_UPDATE_CHANNEL_ID);
            return;
        }

        GuildChannel eventChannel = event.getChannel();
        String channelName = eventChannel != null ? eventChannel.getName() : "Unknown Channel";
        channel.sendMessage(formatReminderText(event, channelName, reminderType))
                .queue(
                        success -> log.info("Posted {} reminder for event ID {}", reminderType, event.getId()),
                        error -> log.error(
                                "Failed to post {} reminder for event ID {}: {}",
                                reminderType,
                                event.getId(),
                                error.getMessage(),
                                error
                        )
                );
    }

    public void postEventUpdate(ScheduledEvent event) {
        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            log.warn("JDA is not initialized. Cannot post event update.");
            return;
        }

        TextChannel channel = jda.getTextChannelById(EVENT_UPDATE_CHANNEL_ID);
        if (channel == null) {
            log.warn("Event channel not found: {}", EVENT_UPDATE_CHANNEL_ID);
            return;
        }

        channel.sendMessage(formatEventText(event))
                .queue(
                        success -> log.info("Posted event update for event ID {}", event.getId()),
                        error -> log.error(
                                "Failed to post event update for event ID {}: {}",
                                event.getId(),
                                error.getMessage(),
                                error
                        )
                );
    }

    private TextChannel requireAnnouncementChannel() {
        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            throw new BusinessException("JDA is not initialized.");
        }

        TextChannel channel = jda.getTextChannelById(EVENT_UPDATE_CHANNEL_ID);
        if (channel == null) {
            throw new BusinessException("Event channel not found: " + EVENT_UPDATE_CHANNEL_ID);
        }
        return channel;
    }

    private String formatReminderText(ScheduledEvent event, String channelName, String reminderType) {
        String eventTime = EventDateTimes.discordTimestamp(event.getStartTime().toInstant());

        String reminderText = switch (reminderType) {
            case "24h" -> "📢 **Event Reminder: 24 Hours Away!**\n\n" +
                    "Don't forget about **" + event.getName() + "** happening tomorrow at " +
                    eventTime + " in **" + channelName + "**";
            case "1h" -> "📢 **Event Reminder: 1 Hour Away!**\n\n" +
                    "The event **" + event.getName() + "** starts in 1 hour at " +
                    eventTime + " in **" + channelName + "**";
            case "15m" -> "📢 **Event Reminder: 15 Minutes Away!**\n\n" +
                    "The event **" + event.getName() + "** starts in 15 minutes at " +
                    eventTime + " in **" + channelName + "**";
            case "start" -> "🚀 **Event Starting Now!**\n\n" +
                    "**" + event.getName() + "** is starting now in **" + channelName + "**! Join us!";
            default -> "📢 **Event Reminder**\n\n" +
                    "The event **" + event.getName() + "** is upcoming in **" + channelName + "**";
        };

        String eventLink = eventUrl(event);
        if (eventLink != null) {
            reminderText += "\n\n" + eventLink;
        }
        return reminderText;
    }

    private String formatEventText(ScheduledEvent event) {
        String description = event.getDescription();
        if (description == null || description.isBlank()) {
            description = "Join us for our next community session!";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🎤 **").append(event.getName()).append("**\n\n");
        sb.append(description).append("\n\n");
        sb.append("📅 **When:** ")
                .append(EventDateTimes.discordTimestamp(event.getStartTime().toInstant()))
                .append("\n");
        sb.append("Event #").append(event.getId());

        String eventLink = eventUrl(event);
        if (eventLink != null) {
            sb.append("\n\n").append(eventLink);
        }
        return sb.toString();
    }

    private String eventUrl(ScheduledEvent event) {
        if (event.getGuild() == null) {
            return null;
        }
        return "https://discord.com/events/" + event.getGuild().getId() + "/" + event.getId();
    }
}
