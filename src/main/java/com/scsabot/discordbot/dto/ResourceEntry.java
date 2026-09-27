package com.scsabot.discordbot.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * One record from data/resources/resource*.json.
 * Matches the 5-field schema: structure, category, title, body, source.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceEntry {

    private String structure;
    private String category;
    private String title;
    private String body;
    private String source;
}