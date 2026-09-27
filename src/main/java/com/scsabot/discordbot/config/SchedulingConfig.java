package com.scsabot.discordbot.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's @Scheduled processing. If your project already has
 *
 * @EnableScheduling somewhere else, delete this class instead of having it
 * twice.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}