package com.scsabot.discordbot.service;

import com.scsabot.discordbot.common.exception.BusinessException;
import com.scsabot.discordbot.entity.Event;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class EventPostService {

    private static final Logger log =
            LoggerFactory.getLogger(EventPostService.class);

    private static final String EVENT_UPDATE_CHANNEL_ID =
            "1543886877512175711";

    private final ObjectProvider<JDA> jdaProvider;

    public EventPostService(
            ObjectProvider<JDA> jdaProvider) {

        this.jdaProvider = jdaProvider;
    }

    /**
     * Posts an event announcement to the dedicated event channel.
     */
    public void postEventAnnouncement(Event event) {

        JDA jda = jdaProvider.getObject();

        if (jda == null) {
            throw new BusinessException("JDA is not initialized.");
        }

        TextChannel channel =
                jda.getTextChannelById(EVENT_UPDATE_CHANNEL_ID);

        if (channel == null) {
            throw new BusinessException(
                    "Event channel not found: " +
                            EVENT_UPDATE_CHANNEL_ID
            );
        }

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

    /**
     * Posts a reminder message for an upcoming event.
     */
    public void postReminder(
            Event event,
            String reminderType) {

        JDA jda = jdaProvider.getObject();

        if (jda == null) {
            log.warn(
                    "JDA is not initialized. Cannot post reminder."
            );
            return;
        }

        TextChannel channel =
                jda.getTextChannelById(EVENT_UPDATE_CHANNEL_ID);

        if (channel == null) {
            log.warn(
                    "Event channel not found for reminder: {}",
                    EVENT_UPDATE_CHANNEL_ID
            );
            return;
        }

        GuildMessageChannel eventChannel =
                (GuildMessageChannel) jda.getGuildChannelById(
                        event.getEventChannelId()
                );

        String channelName =
                eventChannel != null
                        ? eventChannel.getName()
                        : "Unknown Channel";

        String reminderText =
                formatReminderText(
                        event,
                        channelName,
                        reminderType
                );

        channel.sendMessage(reminderText)
                .queue(
                        success -> log.info(
                                "Posted {} reminder for event ID {}",
                                reminderType,
                                event.getId()
                        ),
                        error -> log.error(
                                "Failed to post {} reminder for event ID {}: {}",
                                reminderType,
                                event.getId(),
                                error.getMessage(),
                                error
                        )
                );
    }

    /**
     * Builds a plain-text (non-embed) reminder message. The link is appended on its own
     * line with no surrounding markdown so Discord renders its native link preview.
     * Discord timestamps display the event time according to each user's local timezone.
     */
    private String formatReminderText(
            Event event,
            String channelName,
            String reminderType) {

        String eventTime =
                "<t:" +
                        event.getEventTime().getEpochSecond() +
                        ":F>";

        String reminderText = switch (reminderType) {

            case "24h" -> "📢 **Event Reminder: 24 Hours Away!**\n\n" +
                    "Don't forget about **" +
                    event.getTitle() +
                    "** happening tomorrow at " +
                    eventTime +
                    " in **" +
                    channelName +
                    "**";

            case "1h" -> "📢 **Event Reminder: 1 Hour Away!**\n\n" +
                    "The event **" +
                    event.getTitle() +
                    "** starts in 1 hour at " +
                    eventTime +
                    " in **" +
                    channelName +
                    "**";

            case "15m" -> "📢 **Event Reminder: 15 Minutes Away!**\n\n" +
                    "The event **" +
                    event.getTitle() +
                    "** starts in 15 minutes at " +
                    eventTime +
                    " in **" +
                    channelName +
                    "**";

            case "start" -> "🚀 **Event Starting Now!**\n\n" +
                    "**" +
                    event.getTitle() +
                    "** is starting now in **" +
                    channelName +
                    "**! Join us!";

            default -> "📢 **Event Reminder**\n\n" +
                    "The event **" +
                    event.getTitle() +
                    "** is upcoming in **" +
                    channelName +
                    "**";
        };

        String eventLink = event.getEventLink();
        if (eventLink != null && !eventLink.isBlank()) {
            reminderText += "\n\n" + eventLink;
        }

        return reminderText;
    }

    /**
     * Posts an updated event announcement to the dedicated event channel.
     */
    public void postEventUpdate(Event event) {

        JDA jda = jdaProvider.getObject();

        if (jda == null) {
            log.warn(
                    "JDA is not initialized. Cannot post event update."
            );
            return;
        }

        TextChannel channel =
                jda.getTextChannelById(EVENT_UPDATE_CHANNEL_ID);

        if (channel == null) {
            log.warn(
                    "Event channel not found: {}",
                    EVENT_UPDATE_CHANNEL_ID
            );
            return;
        }

        channel.sendMessage(formatEventText(event))
                .queue(
                        success -> log.info(
                                "Posted event update for event ID {}",
                                event.getId()
                        ),
                        error -> log.error(
                                "Failed to post event update for event ID {}: {}",
                                event.getId(),
                                error.getMessage(),
                                error
                        )
                );
    }

    /**
     * Builds a plain-text (non-embed) view of an event. The link is placed on its own
     * line with no surrounding markdown so Discord renders its native link preview.
     */
    private String formatEventText(Event event) {
        String description = event.getDescription();
        if (description == null || description.isBlank()) {
            description = "Join us for our next community session!";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🎤 **").append(event.getTitle()).append("**\n\n");
        sb.append(description).append("\n\n");
        sb.append("📅 **When:** ")
                .append("<t:").append(event.getEventTime().getEpochSecond()).append(":F>")
                .append("\n");
        sb.append("Event #").append(event.getId());

        String eventLink = event.getEventLink();
        if (eventLink != null && !eventLink.isBlank()) {
            sb.append("\n\n").append(eventLink);
        }

        return sb.toString();
    }
}