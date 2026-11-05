package com.scsabot.discordbot.speakers;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpeakerButtonListener extends ListenerAdapter {

    private final SpeakerListService service;

    public SpeakerButtonListener(SpeakerListService service) {
        this.service = service;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        if (!buttonId.startsWith("speaker:")) {
            return;
        }

        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            return;
        }

        SpeakerSession session = service.get(event.getMessageIdLong());
        if (session == null) {
            reply(event, "This speaker list has expired. Run `/speaker list` again.");
            return;
        }

        long userId = member.getIdLong();

        switch (buttonId) {
            case SpeakerListService.BTN_JOIN -> {
                if (!session.join(userId)) {
                    reply(event, "You're already in the queue.");
                    return;
                }
                update(event, session);
            }

            case SpeakerListService.BTN_LEAVE -> {
                if (!session.leave(userId)) {
                    reply(event, "You're not on the list.");
                    return;
                }
                update(event, session);
            }

            case SpeakerListService.BTN_REFRESH -> {
                AudioChannel channel =
                        guild.getChannelById(AudioChannel.class, session.getVoiceChannelId());
                if (channel == null) {
                    reply(event, "The voice channel no longer exists.");
                    return;
                }
                session.addNew(service.membersByJoinTime(channel));
                repost(event, session);
            }

            case SpeakerListService.BTN_NEXT -> {
                Long current = session.getCurrent();
                boolean isCurrentSpeaker = current != null && current == userId;
                if (!isHost(member, session) && !isCurrentSpeaker) {
                    reply(event, "Only the host or the current speaker can move to the next speaker.");
                    return;
                }
                if (!session.canAdvance()) {
                    reply(event, "There is no one in the queue.");
                    return;
                }
                session.next();
                repost(event, session);
            }

            case SpeakerListService.BTN_CLEAR -> {
                if (!isHost(member, session)) {
                    reply(event, "Only the host can clear the list.");
                    return;
                }
                session.clear();
                announceCleared(event);
            }

            default -> reply(event, "Unknown action.");
        }
    }

    /**
     * The person who ran /speaker list, or anyone who can manage messages.
     */
    private boolean isHost(Member member, SpeakerSession session) {
        return member.getIdLong() == session.getHostId()
                || member.hasPermission(Permission.MESSAGE_MANAGE);
    }

    private void update(ButtonInteractionEvent event, SpeakerSession session) {
        event.editMessageEmbeds(service.buildEmbed(session))
                .setComponents(service.buildComponents())
                .queue();
    }

    /**
     * Refresh posts a brand-new list message instead of editing the old one.
     * The old message just loses its buttons (one small edit), so only the
     * newest list is active, and the session moves over to the new message.
     */
    private void repost(ButtonInteractionEvent event, SpeakerSession session) {
        long oldMessageId = event.getMessageIdLong();

        event.editComponents(List.of())
                .flatMap(hook -> hook.sendMessageEmbeds(service.buildEmbed(session))
                        .setComponents(service.buildComponents()))
                .queue(message -> {
                    service.unregister(oldMessageId);
                    service.register(message.getIdLong(), session);
                });
    }

    /**
     * Clear List: remove the buttons from the old list and post a plain "list cleared" message.
     */
    private void announceCleared(ButtonInteractionEvent event) {
        long oldMessageId = event.getMessageIdLong();

        event.editComponents(List.of())
                .flatMap(hook -> hook.sendMessage("🧹 Speaker list cleared."))
                .queue(message -> service.unregister(oldMessageId));
    }

    private void reply(ButtonInteractionEvent event, String text) {
        event.reply(text).setEphemeral(true).queue();
    }
}