package com.scsabot.discordbot.resources;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bound to the "stamunity.resources.*" keys in application.yml / .properties.
 * Channel IDs are hardcoded in ResourcePoster instead of configured here.
 */
@Data
@Component("resourceBotProperties")
@ConfigurationProperties(prefix = "stamunity.resources")
public class ResourceBotProperties {

    /** Directory to scan, e.g. data/resources */
    private String directory = "data/resources";

    /** Glob pattern for files inside that directory, e.g. resource*.json */
    private String filePattern = "resource*.json";

    /** Cron expression controlling posting times. Default = 9:00 and 21:00 daily */
    private String cron = "0 0 9,21 * * *";

    /** If true, an entry won't repeat until every entry has been posted once */
    private boolean avoidRepeatsUntilExhausted = true;
}