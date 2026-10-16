package com.scsabot.discordbot.read;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scsabot.discordbot.service.RoleService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * /practice read [lang] [difficulty] - Read-aloud + tongue twister practice.
 * <p>
 * Entries are loaded from data/read/*.json. The language is the alphabetic
 * prefix of the file name: english1.json, english2.json -> "english",
 * bangla1.json -> "bangla", german1.json -> "german". Any new language works
 * just by dropping a file in the folder; no code change is needed.
 * <p>
 * Each file is a JSON array of entries:
 * {
 * "id": "...", "title": "...", "language": "en", "difficulty": "easy",
 * "paragraph": "...", "tongue_twisters": ["...", "..."], "tags": ["..."]
 * }
 * <p>
 * If no lang option is given, a random English entry is used.
 * If no difficulty option is given, entries of any difficulty are used.
 * Buttons carry the language and difficulty in their id
 * (e.g. "practice:read:regenerate:bangla:hard") so Regenerate / New Speaker
 * keep the same selection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadaloudCommand {

    public static final String BUTTON_PREFIX = "practice:read:";
    public static final String REGENERATE_BUTTON_ID = BUTTON_PREFIX + "regenerate:";
    public static final String NEW_SPEAKER_BUTTON_ID = BUTTON_PREFIX + "newspeaker:";

    public static final String LANG_OPTION = "lang";
    public static final String DIFFICULTY_OPTION = "difficulty";

    private static final String ROLE_KEY = "readaloud";
    private static final String DEFAULT_LANG = "english";
    private static final String ANY = "any";

    private static final int MAX_DESCRIPTION = 3500;
    private static final int MAX_FIELD = 1000;

    private static final Path DATA_DIR = Path.of("data", "read");
    private static final Pattern FILE_PATTERN =
            Pattern.compile("^(\\p{L}+?)\\d*\\.json$", Pattern.CASE_INSENSITIVE);

    private final RoleService roleService;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * language (lowercase) -> entries
     */
    private final Map<String, List<ReadEntry>> entriesByLang = new ConcurrentHashMap<>();

    private record ReadEntry(String id,
                             String title,
                             String difficulty,
                             String paragraph,
                             List<String> tongueTwisters,
                             List<String> tags) {
    }

    private record Selection(String lang, String difficulty) {
    }

    @PostConstruct
    public void load() {
        entriesByLang.clear();

        if (!Files.isDirectory(DATA_DIR)) {
            log.warn("Read-aloud directory not found: {}", DATA_DIR.toAbsolutePath());
            return;
        }

        try (Stream<Path> files = Files.list(DATA_DIR)) {
            files.filter(Files::isRegularFile).forEach(this::loadFile);
        } catch (IOException e) {
            log.error("Failed to list {}", DATA_DIR, e);
        }

        entriesByLang.forEach((lang, list) ->
                log.info("Read-aloud: loaded {} entries for '{}'", list.size(), lang));
    }

    private void loadFile(Path file) {
        Matcher m = FILE_PATTERN.matcher(file.getFileName().toString());
        if (!m.matches()) {
            log.debug("Skipping read-aloud file with unexpected name: {}", file);
            return;
        }

        String lang = m.group(1).toLowerCase(Locale.ROOT);

        try {
            JsonNode root = mapper.readTree(file.toFile());
            if (root == null || !root.isArray()) {
                log.warn("{} is not a JSON array, skipping", file);
                return;
            }

            List<ReadEntry> target = entriesByLang
                    .computeIfAbsent(lang, k -> Collections.synchronizedList(new ArrayList<>()));

            for (JsonNode node : root) {
                String paragraph = text(node, "paragraph");
                List<String> twisters = textList(node, "tongue_twisters");

                if (paragraph.isBlank() && twisters.isEmpty()) {
                    log.warn("Skipping empty entry in {}", file);
                    continue;
                }

                target.add(new ReadEntry(
                        text(node, "id"),
                        text(node, "title"),
                        text(node, "difficulty").toLowerCase(Locale.ROOT),
                        paragraph,
                        twisters,
                        textList(node, "tags")
                ));
            }
        } catch (IOException e) {
            log.error("Failed to read {}", file, e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText().strip() : "";
    }

    private static List<String> textList(JsonNode node, String field) {
        List<String> out = new ArrayList<>();
        JsonNode arr = node.get(field);
        if (arr != null && arr.isArray()) {
            for (JsonNode item : arr) {
                String s = item.asText().strip();
                if (!s.isBlank()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /**
     * Languages currently available (useful for slash-command autocomplete).
     */
    public List<String> availableLanguages() {
        return entriesByLang.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    // ---------------------------------------------------------------------
    // Handlers
    // ---------------------------------------------------------------------

    /**
     * Handles /practice read [lang] [difficulty].
     */
    public void handleReadCommand(SlashCommandInteractionEvent event) {
        String lang = normalize(
                event.getOption(LANG_OPTION) != null
                        ? event.getOption(LANG_OPTION).getAsString()
                        : DEFAULT_LANG
        );
        String difficulty = normalizeDifficulty(
                event.getOption(DIFFICULTY_OPTION) != null
                        ? event.getOption(DIFFICULTY_OPTION).getAsString()
                        : null
        );

        ReadEntry entry = pickRandomEntry(lang, difficulty);
        if (entry == null) {
            event.reply(missingMessage(lang, difficulty)).setEphemeral(true).queue();
            return;
        }

        event.reply("Generated by " + event.getUser().getAsMention())
                .setAllowedMentions(Collections.emptyList())
                .addEmbeds(buildEmbed(lang, entry).build())
                .addActionRow(actionRow(lang, difficulty))
                .queue();

        roleService.grantRoles(event.getMember(), ROLE_KEY);
    }

    /**
     * "🔄 Regenerate" - same speaker, same language/difficulty, new entry in place.
     */
    public void handleRegenerate(ButtonInteractionEvent event) {
        Selection sel = selectionFromButton(event.getComponentId());
        ReadEntry entry = pickRandomEntry(sel.lang(), sel.difficulty());

        if (entry == null) {
            event.reply(missingMessage(sel.lang(), sel.difficulty())).setEphemeral(true).queue();
            return;
        }

        event.editMessage("🔄 Regenerated by " + event.getUser().getAsMention())
                .setAllowedMentions(Collections.emptyList())
                .setEmbeds(buildEmbed(sel.lang(), entry).build())
                .setActionRow(actionRow(sel.lang(), sel.difficulty()))
                .queue();

        roleService.grantRoles(event.getMember(), ROLE_KEY);
    }

    /**
     * "🗣️ New Speaker" - post a new message, leave the previous one untouched.
     */
    public void handleNewSpeaker(ButtonInteractionEvent event) {
        Selection sel = selectionFromButton(event.getComponentId());
        ReadEntry entry = pickRandomEntry(sel.lang(), sel.difficulty());

        if (entry == null) {
            event.reply(missingMessage(sel.lang(), sel.difficulty())).setEphemeral(true).queue();
            return;
        }

        event.reply("Generated by " + event.getUser().getAsMention())
                .setAllowedMentions(Collections.emptyList())
                .addEmbeds(buildEmbed(sel.lang(), entry).build())
                .addActionRow(actionRow(sel.lang(), sel.difficulty()))
                .queue();

        roleService.grantRoles(event.getMember(), ROLE_KEY);
    }

    /**
     * True if this component id belongs to the read-aloud buttons.
     */
    public static boolean isReadButton(String componentId) {
        return componentId != null && componentId.startsWith(BUTTON_PREFIX);
    }

    /**
     * Routes any read-aloud button to the right handler.
     */
    public void handleButton(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (id.startsWith(REGENERATE_BUTTON_ID)) {
            handleRegenerate(event);
        } else if (id.startsWith(NEW_SPEAKER_BUTTON_ID)) {
            handleNewSpeaker(event);
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Button[] actionRow(String lang, String difficulty) {
        String suffix = lang + ":" + difficulty;
        return new Button[]{
                Button.primary(REGENERATE_BUTTON_ID + suffix, "🔄 Regenerate"),
                Button.secondary(NEW_SPEAKER_BUTTON_ID + suffix, "🗣️ New Speaker")
        };
    }

    private EmbedBuilder buildEmbed(String lang, ReadEntry entry) {
        EmbedBuilder embed = new EmbedBuilder();

        embed.setTitle("📚 Read Aloud");

        StringBuilder desc = new StringBuilder();

        // Badge row: difficulty + tags as pills
        List<String> badges = new ArrayList<>();

        for (String tag : entry.tags()) {
            badges.add(pill(tag));
        }
        if (!badges.isEmpty()) {
            desc.append(String.join(" ", badges)).append("\n\n");
        }

        // Title (bold), then the paragraph as a quote block
        if (!entry.title().isBlank()) {
            desc.append("**").append(entry.title()).append("**\n");
        }
        if (!entry.paragraph().isBlank()) {
            for (String line : truncate(entry.paragraph(), MAX_DESCRIPTION).split("\\R")) {
                desc.append("> ").append(line).append('\n');
            }
        }
        if (!desc.isEmpty()) {
            embed.setDescription(desc.toString().strip());
        }

        // Tongue twisters
        if (!entry.tongueTwisters().isEmpty()) {
            StringBuilder tt = new StringBuilder();
            int n = 1;
            for (String twister : entry.tongueTwisters()) {
                tt.append(n++).append(". ").append(twister).append('\n');
            }
            embed.addField("👅 Tongue Twisters", truncate(tt.toString().strip(), MAX_FIELD), false);
        }

        // Footer: 🌐 English  🟢 Easy
        String footer = "🌐 " + displayName(lang);
        if (!entry.difficulty().isBlank()) {
            footer += "  " + difficultyBadge(entry.difficulty());
        }
        embed.setFooter(truncate(footer, 2000));

        return embed;
    }

    // Inline code renders as a chip/pill in Discord
    private static String pill(String text) {
        return "`" + text.replace("`", "'") + "`";
    }

    private static String difficultyBadge(String difficulty) {
        return switch (difficulty) {
            case "easy" -> "🟢 Easy";
            case "medium" -> "🟡 Medium";
            case "hard" -> "🔴 Hard";
            default -> "🎯 " + difficulty.substring(0, 1).toUpperCase(Locale.ROOT) + difficulty.substring(1);
        };
    }

    private ReadEntry pickRandomEntry(String lang, String difficulty) {
        List<ReadEntry> pool = entriesByLang.get(lang);
        if (pool == null || pool.isEmpty()) {
            return null;
        }

        List<ReadEntry> candidates;
        synchronized (pool) {
            candidates = ANY.equals(difficulty)
                    ? new ArrayList<>(pool)
                    : pool.stream().filter(e -> difficulty.equals(e.difficulty())).toList();
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    /**
     * Button ids look like "practice:read:regenerate:bangla:hard".
     * Old-style ids (no difficulty) still work and fall back to "any".
     */
    private Selection selectionFromButton(String componentId) {
        String[] parts = componentId.split(":");
        String lang = parts.length > 3 ? normalize(parts[3]) : DEFAULT_LANG;
        String difficulty = parts.length > 4 ? normalizeDifficulty(parts[4]) : ANY;
        return new Selection(lang, difficulty);
    }

    private String normalize(String lang) {
        return lang == null || lang.isBlank()
                ? DEFAULT_LANG
                : lang.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeDifficulty(String difficulty) {
        return difficulty == null || difficulty.isBlank()
                ? ANY
                : difficulty.trim().toLowerCase(Locale.ROOT);
    }

    private String displayName(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private String missingMessage(String lang, String difficulty) {
        List<ReadEntry> pool = entriesByLang.get(lang);

        // Language exists but nothing matches the requested difficulty
        if (pool != null && !pool.isEmpty() && !ANY.equals(difficulty)) {
            List<String> levels;
            synchronized (pool) {
                levels = pool.stream()
                        .map(ReadEntry::difficulty)
                        .filter(d -> !d.isBlank())
                        .distinct()
                        .sorted()
                        .toList();
            }
            return "❌ No **" + difficulty + "** entries found for **" + lang + "**. "
                    + (levels.isEmpty() ? "" : "Available difficulties: " + String.join(", ", levels));
        }

        List<String> available = availableLanguages();
        String hint = available.isEmpty()
                ? "No read-aloud files are loaded — check data/read."
                : "Available languages: " + String.join(", ", available);
        return "❌ No entries found for **" + lang + "**. " + hint;
    }
}