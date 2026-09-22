package com.scsabot.discordbot.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TableTopicSession {

    private final String category;
    private final String difficulty;

    private TableTopicQuestion question;
    private String practiceWord;

    public TableTopicSession(
            String category,
            String difficulty,
            TableTopicQuestion question
    ) {
        this.category = category;
        this.difficulty = difficulty;
        this.question = question;
    }

    public void setQuestion(TableTopicQuestion question) {
        this.question = question;
        this.practiceWord = null;
    }
}