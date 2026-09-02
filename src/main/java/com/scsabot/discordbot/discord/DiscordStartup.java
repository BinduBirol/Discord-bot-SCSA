package com.scsabot.discordbot.discord;

import com.scsabot.discordbot.discord.command.DiscordCommandRegistrar;
import com.scsabot.discordbot.voice.VoiceActivityLogger;
import net.dv8tion.jda.api.JDA;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DiscordStartup implements CommandLineRunner {

    private final JDA jda;
    private final DiscordCommandRegistrar commandRegistrar;
    private final VoiceActivityLogger voiceActivityLogger;

    public DiscordStartup(
            JDA jda,
            DiscordCommandRegistrar commandRegistrar,
            VoiceActivityLogger voiceActivityLogger) {

        this.jda = jda;
        this.commandRegistrar = commandRegistrar;
        this.voiceActivityLogger = voiceActivityLogger;
    }

    @Override
    public void run(String... args) {

        commandRegistrar.registerCommands();

        voiceActivityLogger.logBotOnline();
    }
}