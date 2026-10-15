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
 * /practice read [lang] - Read-aloud + tongue twister practice.
 * <p>
 * Entries are loaded from data/read/*.json. The language is the alphabetic
 * prefix of the file name: english1.json, english2.json -> "english",
 * bangla1.json -> "bangla", german1.json -> "german". Any new language works
 * just by dropping a file in the folder; no code change is needed.
 * <p>
 * Each file is a JSON array of entries:
 * {
 * "id": "...", "title": "...", "language": "en",
 * "paragraph": "...", "tongue_twisters": ["...", "..."], "tags": ["..."]
 * }
 * <p>
 * If no lang option is given, a random English entry is used.
 * Buttons carry the language in their id (e.g. "practice:read:regenerate:bangla")
 * so Regenerate / New Speaker keep the same language.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadaloudCommand {

    public static final String BUTTON_PREFIX = "practice:read:";
    public static final String REGENERATE_BUTTON_ID = BUTTON_PREFIX + "regenerate:";
    public static final String NEW_SPEAKER_BUTTON_ID = BUTTON_PREFIX + "newspeaker:";

    public static final String LANG_OPTION = "lang";

    private static final String ROLE_KEY = "readaloud";
    private static final String DEFAULT_LANG = "english";

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
                             String paragraph,
                             List<String> tongueTwisters,
                             List<String> tags) {
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
     * Handles /practice read [lang].
     */
    public void handleReadCommand(SlashCommandInteractionEvent event) {
        String lang = normalize(
                event.getOption(LANG_OPTION) != null
                        ? event.getOption(LANG_OPTION).getAsString()
                        : DEFAULT_LANG
        );

        ReadEntry entry = pickRandomEntry(lang);
        if (entry == null) {
            event.reply(missingMessage(lang)).setEphemeral(true).queue();
            return;
        }

        event.reply("Generated by " + event.getUser().getAsMention())
                .setAllowedMentions(Collections.emptyList())
                .addEmbeds(buildEmbed(lang, entry).build())
                .addActionRow(actionRow(lang))
                .queue();

        roleService.grantRoles(event.getMember(), ROLE_KEY);
    }

    /**
     * "🔄 Regenerate" - same speaker, same language, new entry in place.
     */
    public void handleRegenerate(ButtonInteractionEvent event) {
        String lang = langFromButton(event.getComponentId());
        ReadEntry entry = pickRandomEntry(lang);

        if (entry == null) {
            event.reply(missingMessage(lang)).setEphemeral(true).queue();
            return;
        }

        event.editMessage("🔄 Regenerated by " + event.getUser().getAsMention())
                .setAllowedMentions(Collections.emptyList())
                .setEmbeds(buildEmbed(lang, entry).build())
                .setActionRow(actionRow(lang))
                .queue();

        roleService.grantRoles(event.getMember(), ROLE_KEY);
    }

    /**
     * "🗣️ New Speaker" - post a new message, leave the previous one untouched.
     */
    public void handleNewSpeaker(ButtonInteractionEvent event) {
        String lang = langFromButton(event.getComponentId());
        ReadEntry entry = pickRandomEntry(lang);

        if (entry == null) {
            event.reply(missingMessage(lang)).setEphemeral(true).queue();
            return;
        }

        event.reply("Generated by " + event.getUser().getAsMention())
                .setAllowedMentions(Collections.emptyList())
                .addEmbeds(buildEmbed(lang, entry).build())
                .addActionRow(actionRow(lang))
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

    private Button[] actionRow(String lang) {
        return new Button[]{
                Button.primary(REGENERATE_BUTTON_ID + lang, "🔄 Regenerate"),
                Button.secondary(NEW_SPEAKER_BUTTON_ID + lang, "🗣️ New Speaker")
        };
    }

    public EmbedBuilder buildEmbed(String lang, ReadEntry entry) {
        EmbedBuilder embed = new EmbedBuilder();

        embed.setTitle("📚 Read Aloud");

        // Description: entry title (bold) first, then the paragraph as a quote block
        StringBuilder desc = new StringBuilder();
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

        // Tongue twisters, numbered, one per line
        if (!entry.tongueTwisters().isEmpty()) {
            StringBuilder tt = new StringBuilder();
            int n = 1;
            for (String twister : entry.tongueTwisters()) {
                tt.append(n++).append(". ").append(twister).append('\n');
            }
            embed.addField("👅 Tongue Twisters", truncate(tt.toString().strip(), MAX_FIELD), false);
        }

        // Footer: language + tags
        String footer = "🌐 " + displayName(lang);
        if (!entry.tags().isEmpty()) {
            footer += "  •  🏷️ " + String.join(", ", entry.tags());
        }
        embed.setFooter(truncate(footer, 2000));

        return embed;
    }

    private ReadEntry pickRandomEntry(String lang) {
        List<ReadEntry> pool = entriesByLang.get(lang);
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        synchronized (pool) {
            return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        }
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private String langFromButton(String componentId) {
        int idx = componentId.lastIndexOf(':');
        if (idx < 0 || idx == componentId.length() - 1) {
            return DEFAULT_LANG;
        }
        return normalize(componentId.substring(idx + 1));
    }

    private String normalize(String lang) {
        return lang == null || lang.isBlank()
                ? DEFAULT_LANG
                : lang.trim().toLowerCase(Locale.ROOT);
    }

    private String displayName(String lang) {
        return lang.substring(0, 1).toUpperCase(Locale.ROOT) + lang.substring(1);
    }

    private String missingMessage(String lang) {
        List<String> available = availableLanguages();
        String hint = available.isEmpty()
                ? "No read-aloud files are loaded — check data/read."
                : "Available languages: " + String.join(", ", available);
        return "❌ No entries found for **" + lang + "**. " + hint;
    }
}