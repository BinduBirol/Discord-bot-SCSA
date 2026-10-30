package com.scsabot.discordbot.discord.command;

import com.scsabot.discordbot.event.EventDateTimes;
import com.scsabot.discordbot.service.EventPostService;
import com.scsabot.discordbot.service.EventService;
import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.ScheduledEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class EventCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(EventCommandHandler.class);

    private final EventService eventService;
    private final EventPostService eventPostService;


    public void handleEventCommand(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            replyEphemeral(event, "Please use a valid subcommand (create, list, upcoming).");
            return;
        }

        switch (subcommand) {
            case "upcoming" -> handleUpcomingEvent(event);
            case "import" -> handleImportEvents(event);
            case "announce" -> announceEvent(event);
            default -> replyEphemeral(event, "Unknown subcommand: " + subcommand);
        }
    }


    private boolean hasEventPermission(Member member) {
        return member.hasPermission(Permission.MODERATE_MEMBERS)
                || member.hasPermission(Permission.ADMINISTRATOR);
    }


    public void handleEventCreateModal(ModalInteractionEvent event) {
        try {
            Guild guild = event.getGuild();
            if (guild == null) {
                replyEphemeral(event, "Guild information not available.");
                return;
            }

            String title = event.getValue("event_title").getAsString();
            var descriptionValue = event.getValue("event_description");
            String description = descriptionValue != null ? descriptionValue.getAsString() : null;
            String dateStr = event.getValue("event_date").getAsString();
            String timeStr = event.getValue("event_time").getAsString();
            String eventChannelId = event.getValue("event_channel").getAsString();

            Instant eventTime = EventDateTimes.parse(dateStr, timeStr);
            ScheduledEvent createdEvent = eventService.createEvent(
                    guild,
                    title,
                    description,
                    eventTime,
                    eventChannelId
            );

            eventPostService.postEventAnnouncement(createdEvent);
            event.reply("✅ Created Discord event **" + createdEvent.getName() + "**.").setEphemeral(true).queue();
            log.info("Event created: {} by {}", title, event.getUser().getId());
        } catch (DateTimeParseException e) {
            replyEphemeral(event, "❌ Invalid date/time format. Please use yyyy-MM-dd and HH:mm format.");
            log.warn("Invalid date/time format in event creation: {}", e.getMessage());
        } catch (Exception e) {
            replyEphemeral(event, "❌ Failed to create event: " + e.getMessage());
            log.error("Error creating event from modal: {}", e.getMessage(), e);
        }
    }


    private void handleUpcomingEvent(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            replyEphemeral(event, "Guild information not available.");
            return;
        }

        try {
            var upcomingEvents = eventService.getUpcomingEvents(guild);
            if (upcomingEvents.isEmpty()) {
                replyEphemeral(event, "📭 There are no upcoming events.");
                return;
            }

            var upcomingEvent = upcomingEvents.get(0);
            event.reply(formatEventText(upcomingEvent)).queue();
        } catch (Exception e) {
            if (!event.isAcknowledged()) {
                replyEphemeral(event, "❌ Failed to get the upcoming event.");
            }
            log.error("Error getting the upcoming event", e);
        }
    }

    private String formatEventText(ScheduledEvent event) {
        String description = event.getDescription();
        if (description == null || description.isBlank()) {
            description = "Join us for our next community session!";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🎤 **").append(event.getName()).append("**\n\n");
        //sb.append(description).append("\n\n");
        sb.append("📅 **When:** ").append(EventDateTimes.discordTimestamp(event.getStartTime().toInstant())).append("\n");
        sb.append("Event #").append(event.getId());

        if (event.getGuild() != null) {
            sb.append("\n\n").append("https://discord.com/events/")
                    .append(event.getGuild().getId()).append("/").append(event.getId());
        }
        return sb.toString();
    }

    private void replyEphemeral(SlashCommandInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }

    private void replyEphemeral(ModalInteractionEvent event, String message) {
        event.reply(message).setEphemeral(true).queue();
    }

    private void handleImportEvents(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) {
            replyEphemeral(event, "Member information not available.");
            return;
        }
        if (!hasEventPermission(member)) {
            replyEphemeral(event, "You need moderator or administrator permissions to import events.");
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            replyEphemeral(event, "Guild information not available.");
            return;
        }

    }

    private void announceEvent(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) {
            replyEphemeral(event, "Member information not available.");
            return;
        }
        if (!hasEventPermission(member)) {
            replyEphemeral(event, "You need moderator or administrator permissions to announce events.");
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            replyEphemeral(event, "Guild information not available.");
            return;
        }

        var upcomingEvents = eventService.getUpcomingEvents(guild);
        if (upcomingEvents.isEmpty()) {
            replyEphemeral(event, "📭 There are no upcoming events to announce.");
            return;
        }

        ScheduledEvent upcoming = upcomingEvents.get(0);

        event.deferReply(true).queue();
        try {
            eventPostService.announceAndPublish(upcoming).queue(
                    message -> event.getHook()
                            .editOriginal("✅ Announced and published **" + upcoming.getName() + "**.")
                            .queue(),
                    error -> {
                        log.error("Failed to announce/publish event ID {}: {}",
                                upcoming.getId(), error.getMessage(), error);
                        event.getHook()
                                .editOriginal("❌ Failed to announce event: " + error.getMessage())
                                .queue();
                    }
            );
        } catch (Exception e) {
            event.getHook().editOriginal("❌ " + e.getMessage()).queue();
            log.error("Error announcing event", e);
        }
    }


}
