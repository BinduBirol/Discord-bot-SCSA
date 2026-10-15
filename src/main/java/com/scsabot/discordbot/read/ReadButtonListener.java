package com.scsabot.discordbot.read;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReadButtonListener extends ListenerAdapter {

    private final ReadaloudCommand readaloudCommand;

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!ReadaloudCommand.isReadButton(event.getComponentId())) {
            return;
        }

        readaloudCommand.handleButton(event);
    }
}