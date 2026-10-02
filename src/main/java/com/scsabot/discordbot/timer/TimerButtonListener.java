package com.scsabot.discordbot.timer;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * Routes timer button clicks (Cancel) to TimerCommand.
 */
@Component
@RequiredArgsConstructor
public class TimerButtonListener extends ListenerAdapter {

    private final TimerCommand timerCommand;

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();

        if (id.startsWith(TimerCommand.CANCEL_PREFIX)) {
            timerCommand.handleCancel(event);
        } else if (id.startsWith(TimerCommand.ADD_PREFIX)) {
            timerCommand.handleAddTime(event);
        }
    }
}