package com.scsabot.discordbot.event;

import com.scsabot.discordbot.discord.service.EventInterestService;
import com.scsabot.discordbot.entity.Event;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;

import java.awt.*;

@Component
public class EventCardBuilder {

    private static final Color EVENT_COLOR =
            new Color(155, 89, 182);

    private final EventInterestService eventInterestService;

    public EventCardBuilder(
            EventInterestService eventInterestService) {

        this.eventInterestService = eventInterestService;
    }

    public EmbedBuilder build(com.scsabot.discordbot.entity.Event event) {

        String description = event.getDescription();

        if (description == null || description.isBlank()) {
            description =
                    "Join us for our next community session!";
        }

        int interestedCount =
                eventInterestService.getInterestedCount(
                        event.getId()
                );

        return new EmbedBuilder()
                .setColor(EVENT_COLOR)
                .setTitle("🎤 " + event.getTitle())
                .setDescription(
                        "━━━━━━━━━━━━━━━━━━━━\n" +
                                description +
                                "\n━━━━━━━━━━━━━━━━━━━━"
                )
                .addField(
                        "📅 When",
                        "<t:" +
                                event.getEventTime().getEpochSecond() +
                                ":F>",
                        false
                )
                .addField(
                        "🎙️ Event",
                        "Join us for the session!",
                        false
                )
                .addField(
                        "👥 Interested",
                        String.valueOf(interestedCount),
                        false
                )
                .setFooter(
                        "SCSA Events • Event #" + event.getId()
                )
                .setTimestamp(event.getEventTime());
    }

    public ActionRow buttons(Event event) {

        return ActionRow.of(
                Button.success(
                        "event_interested:" + event.getId(),
                        "Interested"
                )
        );
    }
}