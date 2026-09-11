package com.scsabot.discordbot.discord.command;

import com.scsabot.discordbot.discord.service.EventInterestService;
import com.scsabot.discordbot.event.EventCardBuilder;
import com.scsabot.discordbot.service.EventPostService;
import com.scsabot.discordbot.service.EventService;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.ScheduledEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Component
public class EventCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(EventCommandHandler.class);

    private final String announcementChannelId = "1543886877512175711";

    /**
     * Zone used to interpret the date/time an organizer types into the create modal.
     */
    private static final ZoneId INPUT_ZONE = ZoneId.of("Asia/Dhaka");

    private final EventService eventService;
    private final EventInterestService eventInterestService;
    private final EventCardBuilder eventCardBuilder;
    private final EventPostService eventPostService;

    public EventCommandHandler(
            EventService eventService,
            EventInterestService eventInterestService,
            EventCardBuilder eventCardBuilder,
            EventPostService eventPostService) {

        this.eventService = eventService;
        this.eventInterestService = eventInterestService;
        this.eventCardBuilder = eventCardBuilder;
        this.eventPostService = eventPostService;
    }

    /**
     * Handles the /event command and its subcommands.
     */
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

    /**
     * Handles the /event create subcommand by showing a modal for event creation.
     */
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
                .setMaxLength(2000)
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

        TextInput postChannelInput = TextInput.create("post_channel", "Post Channel ID", TextInputStyle.SHORT)
                .setPlaceholder("The channel where to announce the event")
                .setRequired(true)
                .build();

        return Modal.create("event_create_modal_" + userId + "_" + System.currentTimeMillis(), "Create an Event")
                .addComponents(
                        ActionRow.of(titleInput),
                        ActionRow.of(descriptionInput),
                        ActionRow.of(dateInput),
                        ActionRow.of(timeInput),
                        ActionRow.of(channelInput),
                        ActionRow.of(postChannelInput)
                )
                .build();
    }

    /**
     * Handles the modal submission for event creation.
     */
    public void handleEventCreateModal(ModalInteractionEvent event) {
        try {
            String title = event.getValue("event_title").getAsString();
            String description = event.getValue("event_description").getAsString();
            String dateStr = event.getValue("event_date").getAsString();
            String timeStr = event.getValue("event_time").getAsString();
            String eventChannelId = event.getValue("event_channel").getAsString();
            String postChannelId = event.getValue("post_channel").getAsString();

            Instant eventTime = parseEventDateTime(dateStr, timeStr);

            var createdEvent = eventService.createEvent(
                    event.getGuild().getId(),
                    title,
                    description,
                    eventTime,
                    eventChannelId,
                    postChannelId,
                    event.getUser().getId()
            );

            eventPostService.postEventAnnouncement(createdEvent);

            log.info("Event created: {} by {}", title, event.getUser().getId());
        } catch (DateTimeParseException e) {
            replyEphemeral(event, "❌ Invalid date/time format. Please use yyyy-MM-dd and HH:mm format.");
            log.warn("Invalid date/time format in event creation: {}", e.getMessage());
        } catch (Exception e) {
            replyEphemeral(event, "❌ Failed to create event: " + e.getMessage());
            log.error("Error creating event from modal: {}", e.getMessage(), e);
        }
    }

    /**
     * Handles the /event list subcommand to show upcoming events.
     */
    private void handleListEvents(SlashCommandInteractionEvent event) {

        try {
            var upcomingEvents =
                    eventService.getUpcomingEvents(
                            event.getGuild().getId()
                    );

            if (upcomingEvents.isEmpty()) {
                replyEphemeral(
                        event,
                        "📭 There are no upcoming events."
                );
                return;
            }

            // Discord allows a maximum of 5 action rows per message.
            var eventsToShow = upcomingEvents.stream()
                    .limit(5)
                    .toList();

            // Acknowledge the interaction first.
            event.deferReply().queue(hook -> {

                for (var upcomingEvent : eventsToShow) {

                    hook.sendMessageEmbeds(
                                    eventCardBuilder
                                            .build(upcomingEvent)
                                            .build()
                            )
                            .addActionRow(
                                    eventCardBuilder
                                            .buttons(upcomingEvent)
                                            .getComponents()
                            )
                            .queue();
                }

            });

        } catch (Exception e) {

            if (!event.isAcknowledged()) {
                replyEphemeral(
                        event,
                        "❌ Failed to list events."
                );
            }

            log.error(
                    "Error listing events",
                    e
            );
        }
    }

    /**
     * Handles the /event upcoming subcommand to show the single next event.
     */

    private void handleUpcomingEvent(SlashCommandInteractionEvent event) {

        try {
            var upcomingEvents =
                    eventService.getUpcomingEvents(
                            event.getGuild().getId()
                    );

            if (upcomingEvents.isEmpty()) {
                replyEphemeral(
                        event,
                        "📭 There are no upcoming events."
                );
                return;
            }

            var upcomingEvent = upcomingEvents.get(0);

            eventPostService.postEventAnnouncement(upcomingEvent);

            event.replyEmbeds(
                            eventCardBuilder
                                    .build(upcomingEvent)
                                    .build()
                    )
                    .addActionRow(
                            eventCardBuilder
                                    .buttons(upcomingEvent)
                                    .getComponents()
                    )
                    .queue();

        } catch (Exception e) {

            if (!event.isAcknowledged()) {
                replyEphemeral(
                        event,
                        "❌ Failed to get the upcoming event."
                );
            }

            log.error(
                    "Error getting the upcoming event",
                    e
            );
        }
    }

    public void handleInterestedButton(ButtonInteractionEvent event) {

        String componentId = event.getComponentId();

        if (!componentId.startsWith("event_interested:")) {
            return;
        }

        try {

            Long eventId = Long.parseLong(
                    componentId.substring("event_interested:".length())
            );

            String userId = event.getUser().getId();

            boolean nowInterested =
                    eventInterestService.toggleInterest(
                            eventId,
                            userId
                    );

            int count =
                    eventInterestService.getInterestedCount(eventId);

            if (nowInterested) {

                event.reply(
                        "✅ You're now **interested** in this event!\n" +
                                "👥 **" + count +
                                "** people are interested."
                ).setEphemeral(true).queue();

            } else {

                event.reply(
                        "❌ You're no longer interested in this event.\n" +
                                "👥 **" + count +
                                "** people are interested."
                ).setEphemeral(true).queue();
            }

        } catch (NumberFormatException e) {

            log.warn(
                    "Invalid event ID in interested button: {}",
                    componentId
            );

            event.reply(
                    "❌ This event button is invalid."
            ).setEphemeral(true).queue();
        }
    }


    // ---------- Formatting helpers ----------

    /**
     * Renders an Instant as a Discord timestamp tag. Discord displays this in each
     * viewer's own local timezone/locale automatically — no per-user lookup needed.
     * Format "F" = "Tuesday, 12 September 2026 22:00".
     */
    private String discordTimestamp(Instant instant) {
        return "<t:" + instant.getEpochSecond() + ":F>";
    }

    /**
     * Parses date and time strings, interpreted in the organizer's input zone (Asia/Dhaka),
     * into an absolute Instant. The Instant is what gets stored/displayed — display formatting
     * to a specific zone happens client-side via discordTimestamp().
     */
    private Instant parseEventDateTime(String dateStr, String timeStr) throws DateTimeParseException {
        LocalDateTime localDateTime = LocalDateTime.parse(dateStr + "T" + timeStr + ":00");
        ZonedDateTime zonedDateTime = localDateTime.atZone(INPUT_ZONE);
        return zonedDateTime.toInstant();
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

        var guild = event.getGuild();
        if (guild == null) {
            replyEphemeral(event, "Guild information not available.");
            return;
        }

        guild.retrieveScheduledEvents().queue(
                scheduledEvents -> importScheduledEvents(event, guild.getId(), announcementChannelId, scheduledEvents),
                error -> {
                    log.error("Failed to retrieve Discord Scheduled Events", error);
                    replyEphemeral(event, "❌ Failed to retrieve Discord Scheduled Events.");
                }
        );
    }

    /**
     * Persists each Discord Scheduled Event as a bot Event so it shows up in /event list and /event upcoming.
     * NOTE: no dedupe check is performed here — running this twice will create duplicate rows unless
     * the Event entity tracks the originating Discord scheduled-event ID.
     */
    private void importScheduledEvents(SlashCommandInteractionEvent event, String guildId, String postChannelId,
                                       List<ScheduledEvent> scheduledEvents) {

        if (scheduledEvents.isEmpty()) {
            replyEphemeral(event, "There are no Discord Scheduled Events to import.");
            return;
        }

        StringBuilder sb = new StringBuilder("**Import Results:**\n\n");
        int imported = 0;
        int skipped = 0;

        for (var scheduledEvent : scheduledEvents) {
            var channel = scheduledEvent.getChannel();

            if (channel == null) {
                // EXTERNAL scheduled events (physical location, no voice/stage channel) aren't supported yet.
                sb.append("⏭️ Skipped **").append(scheduledEvent.getName())
                        .append("** — external/location events aren't supported.\n");
                skipped++;
                continue;
            }

            try {
                var createdEvent = eventService.createEvent(
                        guildId,
                        scheduledEvent.getName(),
                        scheduledEvent.getDescription(),
                        scheduledEvent.getStartTime().toInstant(),
                        channel.getId(),
                        postChannelId,
                        event.getUser().getId()
                );

                eventPostService.postEventAnnouncement(createdEvent);


                imported++;
            } catch (Exception e) {
                log.error("Failed to import scheduled event {}", scheduledEvent.getId(), e);
                sb.append("❌ Failed to import **").append(scheduledEvent.getName()).append("**\n");
            }
        }

        sb.append("\n**").append(imported).append(" imported, ").append(skipped).append(" skipped.**");
        replyEphemeral(event, sb.toString());
    }


}