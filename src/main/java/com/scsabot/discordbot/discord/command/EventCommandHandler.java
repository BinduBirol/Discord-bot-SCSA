package com.scsabot.discordbot.discord.command;

import com.scsabot.discordbot.event.EventDateTimes;
import com.scsabot.discordbot.service.EventPostService;
import com.scsabot.discordbot.service.EventService;
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
public class EventCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(EventCommandHandler.class);

    private final EventService eventService;
    private final EventPostService eventPostService;

    public EventCommandHandler(EventService eventService, EventPostService eventPostService) {
        this.eventService = eventService;
        this.eventPostService = eventPostService;
    }

    public void handleEventCommand(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            replyEphemeral(event, "Please use a valid subcommand (create, list, upcoming).");
            return;
        }

        switch (subcommand) {
            case "create" -> handleCreateEvent(event);
            case "list" -> handleListEvents(event);
            case "upcoming" -> handleUpcomingEvent(event);
            case "import" -> handleImportEvents(event);
            default -> replyEphemeral(event, "Unknown subcommand: " + subcommand);
        }
    }

    private void handleCreateEvent(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) {
            replyEphemeral(event, "Member information not available.");
            return;
        }
        if (!hasEventPermission(member)) {
            replyEphemeral(event, "You need moderator or administrator permissions to create events.");
            return;
        }
        event.replyModal(buildCreateEventModal(event.getUser().getId())).queue();
    }

    private boolean hasEventPermission(Member member) {
        return member.hasPermission(Permission.MODERATE_MEMBERS)
                || member.hasPermission(Permission.ADMINISTRATOR);
    }

    private Modal buildCreateEventModal(String userId) {
        TextInput titleInput = TextInput.create("event_title", "Event Title", TextInputStyle.SHORT)
                .setPlaceholder("e.g., Table Topics with Birol")
                .setMaxLength(100)
                .setRequired(true)
                .build();

        TextInput descriptionInput = TextInput.create("event_description", "Description", TextInputStyle.PARAGRAPH)
                .setPlaceholder("e.g., Join us for a fun and spontaneous speaking session.")
                .setMaxLength(1000)
                .setRequired(false)
                .build();

        TextInput dateInput = TextInput.create("event_date", "Date (yyyy-MM-dd)", TextInputStyle.SHORT)
                .setPlaceholder("e.g., 2026-09-12")
                .setRequired(true)
                .build();

        TextInput timeInput = TextInput.create("event_time", "Time (HH:mm)", TextInputStyle.SHORT)
                .setPlaceholder("e.g., 22:00 (Asia/Dhaka timezone)")
                .setRequired(true)
                .build();

        TextInput channelInput = TextInput.create("event_channel", "Voice/Stage Channel ID", TextInputStyle.SHORT)
                .setPlaceholder("The Discord channel ID where the event will happen")
                .setRequired(true)
                .build();

        return Modal.create("event_create_modal_" + userId + "_" + System.currentTimeMillis(), "Create an Event")
                .addComponents(
                        ActionRow.of(titleInput),
                        ActionRow.of(descriptionInput),
                        ActionRow.of(dateInput),
                        ActionRow.of(timeInput),
                        ActionRow.of(channelInput)
                )
                .build();
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

    private void handleListEvents(SlashCommandInteractionEvent event) {
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

            var eventsToShow = upcomingEvents.stream().limit(5).toList();
            event.deferReply().queue(hook -> {
                for (var upcomingEvent : eventsToShow) {
                    hook.sendMessage(formatEventText(upcomingEvent)).queue();
                }
            });
        } catch (Exception e) {
            if (!event.isAcknowledged()) {
                replyEphemeral(event, "❌ Failed to list events.");
            }
            log.error("Error listing events", e);
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
            eventPostService.postEventAnnouncement(upcomingEvent);
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
        sb.append(description).append("\n\n");
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

        guild.retrieveScheduledEvents().queue(
                scheduledEvents -> announceScheduledEvents(event, scheduledEvents),
                error -> {
                    log.error("Failed to retrieve Discord Scheduled Events", error);
                    replyEphemeral(event, "❌ Failed to retrieve Discord Scheduled Events.");
                }
        );
    }

    private void announceScheduledEvents(SlashCommandInteractionEvent event, List<ScheduledEvent> scheduledEvents) {
        if (scheduledEvents.isEmpty()) {
            replyEphemeral(event, "There are no Discord Scheduled Events to announce.");
            return;
        }

        StringBuilder sb = new StringBuilder("**Announcement Results:**\n\n");
        int announced = 0;
        int skipped = 0;

        for (var scheduledEvent : scheduledEvents) {
            if (scheduledEvent.getStatus() == ScheduledEvent.Status.CANCELED
                    || scheduledEvent.getStatus() == ScheduledEvent.Status.COMPLETED) {
                skipped++;
                continue;
            }
            try {
                eventPostService.postEventAnnouncement(scheduledEvent);
                sb.append("✅ Announced **").append(scheduledEvent.getName()).append("**\n");
                announced++;
            } catch (Exception e) {
                log.error("Failed to announce scheduled event {}", scheduledEvent.getId(), e);
                sb.append("❌ Failed to announce **").append(scheduledEvent.getName()).append("**\n");
            }
        }

        sb.append("\n**").append(announced).append(" announced, ").append(skipped).append(" skipped.**");
        replyEphemeral(event, sb.toString());
    }
}
