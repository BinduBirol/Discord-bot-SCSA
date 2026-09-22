package com.scsabot.discordbot.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class DiscordTokenPresentCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String token = context.getEnvironment().getProperty("discord.bot-token", "");
        return token != null && !token.isBlank();
    }
}
