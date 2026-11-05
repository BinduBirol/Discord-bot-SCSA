package com.scsabot.discordbot.speakers;

import com.scsabot.discordbot.speakers.SpeakerSession;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SpeakerListService {

    public static final String BTN_NEXT = "speaker:next";
    public static final String BTN_JOIN = "speaker:join";
    public static final String BTN_LEAVE = "speaker:leave";
    public static final String BTN_REFRESH = "speaker:refresh";
    public static final String BTN_CLEAR = "speaker:clear";

    private static final Color BLUE = new Color(52, 152, 219);
    private static final Color GREEN = new Color(46, 204, 113);
    private static final int MAX_FIELD_CHARS = 900;

    private final VoiceJoinTimeTracker joinTimeTracker;

    /**
     * Key = message id of the speaker list. In memory only: lists reset on restart.
     */
    private final Map<Long, SpeakerSession> sessions = new ConcurrentHashMap<>();

    public SpeakerListService(VoiceJoinTimeTracker joinTimeTracker) {
        this.joinTimeTracker = joinTimeTracker;
    }

    public SpeakerSession createSession(AudioChannel channel, long hostId) {
        SpeakerSession session = new SpeakerSession(channel.getIdLong(), hostId);
        session.addNew(membersByJoinTime(channel));
        return session;
    }

    public void register(long messageId, SpeakerSession session) {
        sessions.values().removeIf(SpeakerSession::isExpired);
        sessions.put(messageId, session);
    }

    public void unregister(long messageId) {
        sessions.remove(messageId);
    }

    public SpeakerSession get(long messageId) {
        return sessions.get(messageId);
    }

    /**
     * Non-bot members of the channel, earliest joiner first.
     */
    public List<Long> membersByJoinTime(AudioChannel channel) {
        return channel.getMembers().stream()
                .filter(member -> !member.getUser().isBot())
                .sorted(Comparator.comparing((Member member) ->
                        joinTimeTracker.getJoinTime(member.getIdLong())))
                .map(Member::getIdLong)
                .toList();
    }

    public MessageEmbed buildEmbed(SpeakerSession session) {
        Long current = session.getCurrent();
        List<Long> queue = session.getQueue();
        List<Long> completed = session.getCompleted();

        String status;
        if (current == null && queue.isEmpty() && !completed.isEmpty()) {
            status = "\n🎉 Everyone has had their turn!";
        } else if (current == null && !queue.isEmpty()) {
            status = "\nPress **Next Speaker** to start.";
        } else {
            status = "";
        }

        return new EmbedBuilder()
                .setColor(current != null ? GREEN : BLUE)
                .setTitle("🎙️ Speaker List")
                .setDescription("Voice channel: <#" + session.getVoiceChannelId() + ">" + status)
                .addField("🎤 Now Speaking",
                        current == null ? "—" : "<@" + current + ">", false)
                .addField("⏳ Remaining (" + queue.size() + ")", format(queue, true), false)
                .addField("✅ Completed (" + completed.size() + ")", format(completed, false), false)
                .setTimestamp(Instant.now())
                .setFooter("StamUnity bot")
                .build();
    }

    public ActionRow[] buildComponents() {
        return new ActionRow[]{
                ActionRow.of(
                        Button.primary(BTN_NEXT, "Next Speaker"),
                        Button.success(BTN_JOIN, "Join Queue"),
                        Button.secondary(BTN_LEAVE, "Remove Me")),
                ActionRow.of(
                        Button.secondary(BTN_REFRESH, "Refresh"),
                        Button.danger(BTN_CLEAR, "Clear List"))
        };
    }

    private String format(List<Long> userIds, boolean numbered) {
        if (userIds.isEmpty()) {
            return "—";
        }

        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Long userId : userIds) {
            String line = (numbered ? (shown + 1) + ". " : "• ") + "<@" + userId + ">\n";
            if (sb.length() + line.length() > MAX_FIELD_CHARS) {
                sb.append("…and ").append(userIds.size() - shown).append(" more");
                break;
            }
            sb.append(line);
            shown++;
        }
        return sb.toString().trim();
    }
}