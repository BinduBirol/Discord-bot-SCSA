package com.scsabot.discordbot.service;

import com.scsabot.discordbot.common.exception.BusinessException;
import com.scsabot.discordbot.event.EventDateTimes;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.ScheduledEvent;
import net.dv8tion.jda.api.entities.channel.concrete.NewsChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.requests.RestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EventPostService {

    private static final Logger log = LoggerFactory.getLogger(EventPostService.class);

    private static final String EVENT_UPDATE_CHANNEL_ID = "1548035146446540990";

    private final ObjectProvider<JDA> jdaProvider;


    public void postEventAnnouncement(ScheduledEvent event) {
        NewsChannel channel = requireAnnouncementChannel();
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

        NewsChannel channel = jda.getNewsChannelById(EVENT_UPDATE_CHANNEL_ID);
        if (channel == null) {
            log.warn("Announcement channel not found for reminder: {}", EVENT_UPDATE_CHANNEL_ID);
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

        NewsChannel channel = jda.getNewsChannelById(EVENT_UPDATE_CHANNEL_ID);
        if (channel == null) {
            log.warn("Announcement channel not found: {}", EVENT_UPDATE_CHANNEL_ID);
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

    private NewsChannel requireAnnouncementChannel() {
        JDA jda = jdaProvider.getIfAvailable();
        if (jda == null) {
            throw new BusinessException("JDA is not initialized.");
        }

        NewsChannel channel = jda.getNewsChannelById(EVENT_UPDATE_CHANNEL_ID);
        if (channel == null) {
            throw new BusinessException("Announcement channel not found: " + EVENT_UPDATE_CHANNEL_ID);
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
        long startEpoch = event.getStartTime().toEpochSecond();

        StringBuilder sb = new StringBuilder();
        sb.append("🎤 **").append(event.getName()).append("**\n\n");
        sb.append("📅 **When:** ")
                .append(EventDateTimes.discordTimestamp(event.getStartTime().toInstant()))
                .append("\n");
        sb.append("⏳ **Starts:** <t:").append(startEpoch).append(":R>\n");
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

    /**
     * Posts the event announcement to the news channel and publishes (crossposts) it
     * so that servers following the channel receive it.
     */
    public RestAction<Message> announceAndPublish(ScheduledEvent event) {
        NewsChannel channel = requireAnnouncementChannel();
        return channel.sendMessage(formatEventText(event))
                .flatMap(Message::crosspost)
                .onSuccess(message -> log.info(
                        "Announced and published event ID {} with message ID {}",
                        event.getId(), message.getId()));
    }
}