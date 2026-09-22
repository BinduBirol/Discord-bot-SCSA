package com.scsabot.discordbot.tabletopics;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scsabot.discordbot.dto.TableTopicQuestion;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@Component
public class TableTopicDataLoader {

    private static final Logger log =
            LoggerFactory.getLogger(TableTopicDataLoader.class);

    private static final Path QUESTIONS_PATH =
            Path.of("data/questions");

    private static final Path WORDS_PATH =
            Path.of("data/words");

    private final ObjectMapper objectMapper;

    private final List<TableTopicQuestion> questions =
            new ArrayList<>();

    private final List<String> words =
            new ArrayList<>();

    public TableTopicDataLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        loadQuestions();
        loadWords();

        log.info(
                "TableTopic loaded {} questions and {} words",
                questions.size(),
                words.size()
        );
    }

    private void loadQuestions() {

        if (!Files.exists(QUESTIONS_PATH)) {
            log.warn(
                    "TableTopic questions directory does not exist: {}",
                    QUESTIONS_PATH
            );
            return;
        }

        try (Stream<Path> files = Files.list(QUESTIONS_PATH)) {

            files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName()
                            .toString()
                            .startsWith("question"))
                    .filter(path -> path.getFileName()
                            .toString()
                            .endsWith(".json"))
                    .forEach(this::loadQuestionFile);

        } catch (IOException e) {
            log.error(
                    "Failed to scan TableTopic question directory",
                    e
            );
        }
    }

    private void loadQuestionFile(Path path) {

        try (InputStream inputStream = Files.newInputStream(path)) {

            List<TableTopicQuestion> loaded =
                    objectMapper.readValue(
                            inputStream,
                            new TypeReference<List<TableTopicQuestion>>() {
                            }
                    );

            if (loaded == null) {
                return;
            }

            loaded.stream()
                    .filter(this::validQuestion)
                    .forEach(questions::add);

            log.info(
                    "Loaded {} TableTopic questions from {}",
                    loaded.size(),
                    path
            );

        } catch (Exception e) {

            log.error(
                    "Failed to load TableTopic question file: {}",
                    path,
                    e
            );
        }
    }

    private void loadWords() {

        if (!Files.exists(WORDS_PATH)) {
            log.warn(
                    "TableTopic words directory does not exist: {}",
                    WORDS_PATH
            );
            return;
        }

        try (Stream<Path> files = Files.list(WORDS_PATH)) {

            files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName()
                            .toString()
                            .startsWith("word"))
                    .filter(path -> path.getFileName()
                            .toString()
                            .endsWith(".json"))
                    .forEach(this::loadWordFile);

        } catch (IOException e) {
            log.error(
                    "Failed to scan TableTopic word directory",
                    e
            );
        }
    }

    private void loadWordFile(Path path) {

        try (InputStream inputStream = Files.newInputStream(path)) {

            List<String> loaded =
                    objectMapper.readValue(
                            inputStream,
                            new TypeReference<List<String>>() {
                            }
                    );

            if (loaded == null) {
                return;
            }

            loaded.stream()
                    .filter(word -> word != null && !word.isBlank())
                    .map(String::trim)
                    .forEach(words::add);

            log.info(
                    "Loaded {} TableTopic words from {}",
                    loaded.size(),
                    path
            );

        } catch (Exception e) {

            log.error(
                    "Failed to load TableTopic word file: {}",
                    path,
                    e
            );
        }
    }

    private boolean validQuestion(TableTopicQuestion question) {

        return question != null
                && question.question() != null
                && !question.question().isBlank()
                && question.category() != null
                && !question.category().isBlank()
                && question.difficulty() != null
                && !question.difficulty().isBlank();
    }

    public List<TableTopicQuestion> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    public List<String> getWords() {
        return Collections.unmodifiableList(words);
    }
}