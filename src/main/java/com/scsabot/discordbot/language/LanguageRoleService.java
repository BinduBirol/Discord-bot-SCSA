package com.scsabot.discordbot.language;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Role management for spoken languages. Discord roles are the only storage.
 */
@Service
public class LanguageRoleService {

    public static final String ROLE_PREFIX = "Language: ";
    public static final int MAX_CUSTOM_LANGUAGES = 100;

    public static final List<String> DEFAULT_LANGUAGES = List.of(
            "English", "Bangla", "Hindi", "Urdu", "Arabic", "Spanish", "French", "German",
            "Chinese", "Japanese", "Korean", "Russian", "Portuguese", "Turkish",
            "Indonesian", "Italian", "Tamil", "Nepali");

    /**
     * 2-40 chars: letters (any script), combining marks, spaces, hyphens, apostrophes, parentheses.
     */
    private static final Pattern VALID =
            Pattern.compile("^[\\p{L}\\p{M}][\\p{L}\\p{M} \\-'()]{1,39}$");

    /**
     * guildId:languageKey -> role future. Prevents duplicate creation under concurrency.
     */
    private final ConcurrentMap<String, CompletableFuture<Role>> inFlight = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------- lookup

    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public boolean isLanguageRole(Role role) {
        return !role.isManaged() && role.getName().startsWith(ROLE_PREFIX)
                && !displayName(role).isBlank();
    }

    public String displayName(Role role) {
        return role.getName().substring(ROLE_PREFIX.length()).trim();
    }

    public Role findRole(Guild guild, String display) {
        String k = key(display);
        return guild.getRoles().stream()
                .filter(this::isLanguageRole)
                .filter(r -> key(displayName(r)).equals(k))
                .findFirst().orElse(null);
    }

    public boolean isDefault(String display) {
        return DEFAULT_LANGUAGES.stream().anyMatch(d -> key(d).equals(key(display)));
    }

    /**
     * Defaults first (fixed order), then custom languages alphabetically.
     */
    public List<String> availableLanguages(Guild guild) {
        Map<String, String> result = new LinkedHashMap<>();
        DEFAULT_LANGUAGES.forEach(d -> result.put(key(d), d));
        guild.getRoles().stream()
                .filter(this::isLanguageRole)
                .map(this::displayName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(n -> result.putIfAbsent(key(n), n));
        return new ArrayList<>(result.values());
    }

    public Set<String> memberLanguageKeys(Member member) {
        return member.getRoles().stream()
                .filter(this::isLanguageRole)
                .map(r -> key(displayName(r)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private long customRoleCount(Guild guild) {
        return guild.getRoles().stream()
                .filter(this::isLanguageRole)
                .filter(r -> !isDefault(displayName(r)))
                .count();
    }

    // ----------------------------------------------------------- normalizing

    /**
     * Returns a cleaned display name, or null if invalid.
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim().replaceAll("\\s+", " ");
        if (!VALID.matcher(s).matches()) return null;

        StringBuilder out = new StringBuilder();
        for (String word : s.split(" ")) {
            if (out.length() > 0) out.append(' ');
            int end = word.offsetByCodePoints(0, 1);
            out.append(word.substring(0, end).toUpperCase(Locale.ROOT)).append(word.substring(end));
        }
        return out.toString();
    }

    // ------------------------------------------------------------ role create

    /**
     * Returns the existing "Language: X" role or creates it. Concurrent callers for the same
     * guild+language share one future, so a role is never created twice.
     */
    public CompletableFuture<Role> getOrCreateRole(Guild guild, String display) {
        String cacheKey = guild.getId() + ":" + key(display);
        return inFlight.compute(cacheKey, (k, existing) -> {
            if (existing != null && !existing.isCompletedExceptionally()) {
                if (!existing.isDone()) return existing;
                Role cached = existing.getNow(null);
                if (cached != null && guild.getRoleById(cached.getIdLong()) != null) return existing;
            }
            return createOrFind(guild, display);
        });
    }

    private CompletableFuture<Role> createOrFind(Guild guild, String display) {
        Role found = findRole(guild, display);
        if (found != null) return CompletableFuture.completedFuture(found);

        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_ROLES)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("I need the **Manage Roles** permission to create language roles."));
        }
        return guild.createRole()
                .setName(ROLE_PREFIX + display)
                .setPermissions(0L)
                .setMentionable(false)
                .setHoisted(false)
                .submit();
    }

    // ----------------------------------------------------- user selection ops

    /**
     * Applies a selection made on one page of options. Only languages shown on that page are
     * added/removed, so selections made on other pages are never lost.
     *
     * @return the member's full set of language keys after the change
     */
    public CompletableFuture<Set<String>> applyPageSelection(Member member,
                                                             Collection<String> pageNames,
                                                             Collection<String> selectedNames) {
        Guild guild = member.getGuild();
        Set<String> pageKeys = pageNames.stream().map(LanguageRoleService::key).collect(Collectors.toSet());

        Map<String, String> selected = new LinkedHashMap<>();
        for (String n : selectedNames) {
            if (pageKeys.contains(key(n))) selected.put(key(n), n);
        }

        Set<String> current = memberLanguageKeys(member);

        List<Role> toRemove = member.getRoles().stream()
                .filter(this::isLanguageRole)
                .filter(r -> {
                    String k = key(displayName(r));
                    return pageKeys.contains(k) && !selected.containsKey(k);
                })
                .collect(Collectors.toList());

        List<CompletableFuture<Role>> adds = selected.entrySet().stream()
                .filter(e -> !current.contains(e.getKey()))
                .map(e -> getOrCreateRole(guild, e.getValue()))
                .collect(Collectors.toList());

        Set<String> result = new LinkedHashSet<>(current);
        toRemove.forEach(r -> result.remove(key(displayName(r))));
        result.addAll(selected.keySet());

        return CompletableFuture.allOf(adds.toArray(new CompletableFuture[0])).thenCompose(v -> {
            List<Role> toAdd = adds.stream().map(CompletableFuture::join).collect(Collectors.toList());
            if (toAdd.isEmpty() && toRemove.isEmpty()) {
                return CompletableFuture.completedFuture(result);
            }
            return guild.modifyMemberRoles(member, toAdd, toRemove).submit().thenApply(x -> result);
        });
    }

    /**
     * Makes the member's language roles match {@code keys} exactly, across ALL languages.
     * Used when a member presses Save in the picker.
     *
     * @return the member's full set of language keys after the change
     */
    public CompletableFuture<Set<String>> applySelection(Member member, Set<String> keys) {
        List<String> all = availableLanguages(member.getGuild());
        List<String> chosen = all.stream()
                .filter(name -> keys.contains(key(name)))
                .toList();
        return applyPageSelection(member, all, chosen);
    }

    public static final class AddResult {
        public final String display;
        public final boolean newToServer;
        public final boolean alreadyHad;

        AddResult(String display, boolean newToServer, boolean alreadyHad) {
            this.display = display;
            this.newToServer = newToServer;
            this.alreadyHad = alreadyHad;
        }
    }

    /**
     * Custom-language flow: ensure the role exists (making it available to everyone) and assign it.
     */
    public CompletableFuture<AddResult> addLanguage(Member member, String normalized) {
        return addLanguage(member, normalized, true);
    }

    /**
     * Same as above, but with {@code assign = false} it only makes sure the role exists
     * (available to everyone) and does NOT give it to the member. Used by the picker,
     * where the language is added to the member's unsaved selection until they press Save.
     */
    public CompletableFuture<AddResult> addLanguage(Member member, String normalized, boolean assign) {
        Guild guild = member.getGuild();
        String k = key(normalized);
        String display = availableLanguages(guild).stream()
                .filter(n -> key(n).equals(k)).findFirst().orElse(normalized);

        boolean exists = findRole(guild, display) != null;
        if (!exists && !isDefault(display) && customRoleCount(guild) >= MAX_CUSTOM_LANGUAGES) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "This server has reached the limit of " + MAX_CUSTOM_LANGUAGES + " custom languages."));
        }
        boolean newToServer = !exists && !isDefault(display);

        return getOrCreateRole(guild, display).thenCompose(role -> {
            boolean has = member.getRoles().contains(role);
            if (has || !assign) {
                return CompletableFuture.completedFuture(new AddResult(display, newToServer, has));
            }
            return guild.addRoleToMember(member, role).submit()
                    .thenApply(v -> new AddResult(display, newToServer, false));
        });
    }
}