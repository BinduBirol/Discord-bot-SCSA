package com.scsabot.discordbot.discord;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteDeleteEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Slf4j
@Component
public class InviteTracker extends ListenerAdapter {

    public record InviteSnapshot(String code, int uses, int maxUses, Long inviterId) {
    }

    public record InviteSource(String code, Long inviterId, int uses) {
    }

    private record Deleted(InviteSnapshot snapshot, long at) {
    }

    private static final long DELETED_TTL_MS = 15_000;

    // guildId -> (code -> snapshot)
    private final Map<Long, Map<String, InviteSnapshot>> snapshots = new ConcurrentHashMap<>();
    // guildId -> (code -> recently deleted invite), for max-use invites consumed by a join
    private final Map<Long, Map<String, Deleted>> recentlyDeleted = new ConcurrentHashMap<>();
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    private Object lock(Guild guild) {
        return locks.computeIfAbsent(guild.getIdLong(), k -> new Object());
    }

    private static InviteSnapshot toSnapshot(Invite i) {
        return new InviteSnapshot(
                i.getCode(), i.getUses(), i.getMaxUses(),
                i.getInviter() != null ? i.getInviter().getIdLong() : null);
    }

    private static Map<String, InviteSnapshot> toMap(List<Invite> invites) {
        Map<String, InviteSnapshot> map = new HashMap<>();
        invites.forEach(i -> map.put(i.getCode(), toSnapshot(i)));
        return map;
    }

    // ---- seeding -------------------------------------------------------------

    @Override
    public void onReady(ReadyEvent event) {
        event.getJDA().getGuilds().forEach(this::refresh);
    }

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        refresh(event.getGuild());
    }

    /**
     * Public so you can call it manually if the listener is registered after JDA is ready.
     */
    public void refresh(Guild guild) {
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_SERVER)) {
            log.warn("InviteTracker: missing MANAGE_SERVER in guild {}", guild.getId());
            return;
        }
        guild.retrieveInvites().queue(invites -> {
            synchronized (lock(guild)) {
                snapshots.put(guild.getIdLong(), toMap(invites));
            }
            log.info("InviteTracker: seeded {} invites for guild {}", invites.size(), guild.getId());
        }, err -> log.warn("InviteTracker: failed to seed guild {}", guild.getId(), err));
    }

    // ---- live updates --------------------------------------------------------

    @Override
    public void onGuildInviteCreate(GuildInviteCreateEvent event) {
        synchronized (lock(event.getGuild())) {
            snapshots.computeIfAbsent(event.getGuild().getIdLong(), k -> new ConcurrentHashMap<>())
                    .put(event.getCode(), toSnapshot(event.getInvite()));
        }
    }

    @Override
    public void onGuildInviteDelete(GuildInviteDeleteEvent event) {
        long guildId = event.getGuild().getIdLong();
        synchronized (lock(event.getGuild())) {
            Map<String, InviteSnapshot> snap = snapshots.get(guildId);
            if (snap == null) return;
            InviteSnapshot removed = snap.remove(event.getCode());
            if (removed != null) {
                recentlyDeleted.computeIfAbsent(guildId, k -> new ConcurrentHashMap<>())
                        .put(removed.code(), new Deleted(removed, System.currentTimeMillis()));
            }
        }
    }

    // ---- resolving -----------------------------------------------------------

    /**
     * Resolves which invite was used for this join. Callback gets null if unknown.
     */
    public void resolveInvite(Guild guild, Consumer<InviteSource> callback) {
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_SERVER)) {
            callback.accept(null);
            return;
        }

        guild.retrieveInvites().queue(invites -> {
            InviteSource result = null;

            synchronized (lock(guild)) {
                long guildId = guild.getIdLong();
                Map<String, InviteSnapshot> fresh = toMap(invites);
                Map<String, InviteSnapshot> old = snapshots.get(guildId);

                if (old == null) {
                    // No baseline: never guess, just seed for next time.
                    snapshots.put(guildId, fresh);
                    log.warn("InviteTracker: no baseline for guild {}, seeded now", guildId);
                } else {
                    // 1) An existing invite whose use count went up
                    InviteSnapshot used = null;
                    for (InviteSnapshot now : fresh.values()) {
                        InviteSnapshot before = old.get(now.code());
                        int prev = before == null ? 0 : before.uses();
                        if (now.uses() > prev) {
                            used = now;
                            break;
                        }
                    }

                    // 2) A max-use invite deleted in the last few seconds
                    Deleted usedDeleted = null;
                    if (used == null) {
                        long cutoff = System.currentTimeMillis() - DELETED_TTL_MS;
                        Map<String, Deleted> deleted =
                                recentlyDeleted.getOrDefault(guildId, new ConcurrentHashMap<>());
                        deleted.values().removeIf(d -> d.at() < cutoff);
                        for (Deleted d : deleted.values()) {
                            InviteSnapshot s = d.snapshot();
                            if (s.maxUses() > 0 && s.uses() + 1 >= s.maxUses()) {
                                usedDeleted = d;
                                break;
                            }
                        }
                        if (usedDeleted != null) {
                            deleted.remove(usedDeleted.snapshot().code());
                        }
                    }

                    if (used != null) {
                        result = new InviteSource(used.code(), used.inviterId(), used.uses());
                    } else if (usedDeleted != null) {
                        InviteSnapshot s = usedDeleted.snapshot();
                        result = new InviteSource(s.code(), s.inviterId(), s.maxUses());
                    }

                    // New baseline: take fresh values, but only "consume" the matched invite.
                    // Other invites that also went up (a concurrent join) keep their old count,
                    // so the next resolve attributes them correctly.
                    Map<String, InviteSnapshot> next = new HashMap<>();
                    for (InviteSnapshot now : fresh.values()) {
                        InviteSnapshot before = old.get(now.code());
                        boolean isMatched = used != null && used.code().equals(now.code());
                        if (isMatched || before == null || now.uses() <= before.uses()) {
                            next.put(now.code(), now);
                        } else {
                            next.put(now.code(), before); // pending increment
                        }
                    }
                    snapshots.put(guildId, next);

                    log.debug("InviteTracker: guild {} resolved -> {}", guildId, result);
                }
            }
            callback.accept(result);
        }, err -> {
            log.warn("InviteTracker: retrieveInvites failed", err);
            callback.accept(null);
        });
    }
}