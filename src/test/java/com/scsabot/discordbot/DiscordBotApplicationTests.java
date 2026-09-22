package com.scsabot.discordbot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "discord.bot-token=",
        "spring.task.scheduling.enabled=false"
})
@ActiveProfiles("test")
class DiscordBotApplicationTests {

    @Test
    void contextLoads() {
    }
}
