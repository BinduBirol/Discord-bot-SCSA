package com.scsabot.discordbot.language;

import net.dv8tion.jda.api.entities.Member;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Holds each member's UNSAVED language selection while they use the private picker.
 * Nothing touches Discord roles until the member presses Save.
 * <p>
 * In-memory only: drafts expire after 30 minutes and are lost on restart
 * (the Save button then tells the member to reopen the picker).
 */
@Component
public class LanguageDraftStore {

    private static final long TTL_MILLIS = TimeUnit.MINUTES.toMillis(30);

    private static final class Draft {
        final Set<String> keys;
        final long touchedAt = System.currentTimeMillis();

        Draft(Collection<String> keys) {
            this.keys = Set.copyOf(keys);
        }
    }

    /**
     * "guildId:userId" -> draft (set of language keys).
     */
    private final ConcurrentMap<String, Draft> drafts = new ConcurrentHashMap<>();

    private static String id(Member member) {
        return member.getGuild().getId() + ":" + member.getId();
    }

    /**
     * Stores (replaces) the member's draft.
     */
    public void set(Member member, Collection<String> keys) {
        purgeExpired();
        drafts.put(id(member), new Draft(keys));
    }

    /**
     * Returns the member's draft keys, or null if there is none or it expired.
     */
    public Set<String> get(Member member) {
        String id = id(member);
        Draft draft = drafts.get(id);
        if (draft == null) return null;
        if (isExpired(draft)) {
            drafts.remove(id, draft);
            return null;
        }
        return draft.keys;
    }

    public void clear(Member member) {
        drafts.remove(id(member));
    }

    private static boolean isExpired(Draft draft) {
        return System.currentTimeMillis() - draft.touchedAt > TTL_MILLIS;
    }

    private void purgeExpired() {
        drafts.values().removeIf(LanguageDraftStore::isExpired);
    }
}