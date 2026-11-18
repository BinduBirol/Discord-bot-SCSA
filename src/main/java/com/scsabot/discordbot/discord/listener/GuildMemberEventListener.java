package com.scsabot.discordbot.discord.listener;

import com.scsabot.discordbot.service.WelcomeService;
import com.scsabot.discordbot.voice.VoiceActivityLogger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.stream.Collectors;

import static java.awt.Color.ORANGE;

@Component
public class GuildMemberEventListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(GuildMemberEventListener.class);

    private final VoiceActivityLogger voiceActivityLogger;
    private final WelcomeService welcomeService;
    private final TaskExecutor discordEventExecutor;

    @Value("${discord.welcome-thread-id}")
    private long welcomeThreadId;

    @Value("${discord.dm-forward-channel-id}")
    private long dmForwardChannelId;

    public GuildMemberEventListener(
            VoiceActivityLogger voiceActivityLogger,
            WelcomeService welcomeService,
            @Qualifier("discordEventExecutor") TaskExecutor discordEventExecutor) {
        this.voiceActivityLogger = voiceActivityLogger;
        this.welcomeService = welcomeService;
        this.discordEventExecutor = discordEventExecutor;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Member member = event.getMember();
        log.info("Guild member joined: {} ({})",
                member.getEffectiveName(), member.getIdLong());

        discordEventExecutor.execute(() -> {
            voiceActivityLogger.logMemberJoin(member);
            welcomeService.welcomeMember(member);

            var thread = event.getGuild().getThreadChannelById(welcomeThreadId);

            if (thread == null) {
                log.warn("Welcome thread not found: {}", welcomeThreadId);
                return;
            }

        });
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        User user = event.getUser();
        log.info("Guild member left: {} ({})", user.getName(), user.getId());
        discordEventExecutor.execute(() -> {
            voiceActivityLogger.logMemberLeave(user);
            voiceActivityLogger.sendLeaveDm(user, event.getGuild().getName());
        });
    }


    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromType(ChannelType.PRIVATE)) return;

        TextChannel channel = event.getJDA().getTextChannelById(dmForwardChannelId);
        if (channel == null) {
            log.warn("DM forward channel {} not found", dmForwardChannelId);
            return;
        }

        User user = event.getAuthor();
        Message message = event.getMessage();
        String content = message.getContentDisplay();

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(ORANGE)
                .setTitle("New DM to the bot")
                .setDescription(content.isBlank() ? "(no text)" : content)
                .addField("From", user.getAsMention() + " (" + user.getName() + ")", false)
                .setFooter("ID: " + user.getId())
                .setTimestamp(Instant.now());

        if (!message.getAttachments().isEmpty()) {
            String files = message.getAttachments().stream()
                    .map(Message.Attachment::getUrl)
                    .collect(Collectors.joining("\n"));
            embed.addField("Attachments", files, false);
        }

        channel.sendMessageEmbeds(embed.build()).queue();

        event.getChannel().sendMessage(
                "Hi! I'm just a bot, so I can't reply to you directly. "
                        + "I was created by my master Birol (username: **bindubirol**, user ID: `608593805901561857`). "
                        + "Please contact him directly if you have any questions or problems."
        ).queue();
    }
}
