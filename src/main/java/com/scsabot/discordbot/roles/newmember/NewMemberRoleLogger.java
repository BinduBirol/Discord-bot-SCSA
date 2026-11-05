package com.scsabot.discordbot.roles.newmember;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class NewMemberRoleLogger {

    private static final String VOICE_ACTIVITY_CHANNEL_ID = "1544618036047122442";

    private static final Color BLUE = new Color(52, 152, 219);
    private static final Color RED = new Color(231, 76, 60);

    private static final int MAX_LISTED_MEMBERS = 30;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.of("Asia/Dhaka"));

    private final ObjectProvider<JDA> jdaProvider;

    public NewMemberRoleLogger(ObjectProvider<JDA> jdaProvider) {
        this.jdaProvider = jdaProvider;
    }

    private TextChannel getActivityChannel() {
        return jdaProvider.getObject()
                .getTextChannelById(VOICE_ACTIVITY_CHANNEL_ID);
    }

    public void logRoleCleanup(
            String roleId,
            List<Member> removed,
            int failedCount) {

        TextChannel channel = getActivityChannel();

        if (channel == null) {
            return;
        }

        StringBuilder description = new StringBuilder();
        description.append("Removed <@&").append(roleId).append("> from **")
                .append(removed.size())
                .append("** member(s) who completed 1 month in the server.\n\n");

        int listed = 0;
        for (Member member : removed) {
            if (listed >= MAX_LISTED_MEMBERS) {
                description.append("…and ")
                        .append(removed.size() - listed)
                        .append(" more");
                break;
            }
            description.append("• ")
                    .append(member.getAsMention())
                    .append(" (joined ")
                    .append(DATE_FORMAT.format(member.getTimeJoined()))
                    .append(")\n");
            listed++;
        }

        if (failedCount > 0) {
            description.append("\n⚠️ Failed to remove the role from **")
                    .append(failedCount)
                    .append("** member(s). Check bot permissions and role hierarchy.");
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(failedCount > 0 ? RED : BLUE)
                .setTitle("New Member Role Cleanup")
                .setDescription(description.toString())
                .setTimestamp(Instant.now())
                .setFooter("StamUnity bot");

        channel.sendMessageEmbeds(embed.build()).queue();
    }
}
