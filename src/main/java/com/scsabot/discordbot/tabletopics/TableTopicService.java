package com.scsabot.discordbot.tabletopics;

import com.scsabot.discordbot.dto.TableTopicQuestion;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TableTopicService {

    private final TableTopicDataLoader dataLoader;

    public TableTopicService(TableTopicDataLoader dataLoader) {
        this.dataLoader = dataLoader;
    }

    public TableTopicQuestion randomQuestion(
            String category,
            String difficulty,
            TableTopicQuestion current
    ) {

        List<TableTopicQuestion> candidates =
                dataLoader.getQuestions()
                        .stream()
                        .filter(question ->
                                matches(question, category, difficulty))
                        .toList();

        if (candidates.isEmpty()) {
            return null;
        }

        // Avoid immediately returning the same question.
        if (candidates.size() > 1 && current != null) {
            candidates = candidates.stream()
                    .filter(question ->
                            !question.question()
                                    .equals(current.question()))
                    .toList();
        }

        return randomElement(candidates);
    }

    public String randomWord() {

        List<String> words = dataLoader.getWords();

        if (words.isEmpty()) {
            return null;
        }

        return randomElement(words);
    }

    private boolean matches(
            TableTopicQuestion question,
            String category,
            String difficulty
    ) {

        boolean categoryMatches =
                category == null
                        || category.isBlank()
                        || question.category()
                        .equalsIgnoreCase(category);

        boolean difficultyMatches =
                difficulty == null
                        || difficulty.isBlank()
                        || question.difficulty()
                        .equalsIgnoreCase(difficulty);

        return categoryMatches && difficultyMatches;
    }

    private <T> T randomElement(List<T> values) {

        return values.get(
                ThreadLocalRandom.current()
                        .nextInt(values.size())
        );
    }
}