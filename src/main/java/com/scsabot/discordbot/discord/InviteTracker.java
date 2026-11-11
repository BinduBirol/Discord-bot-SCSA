package com.scsabot.discordbot.discord;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Invite;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.invite.GuildInviteCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Component
public class InviteTracker extends ListenerAdapter {

    public record InviteSnapshot(String code, int uses, int maxUses, Long inviterId) {
    }

    public record InviteSource(String code, Long inviterId, int uses) {
    }

    // guildId -> (code -> snapshot)
    private final Map<Long, Map<String, InviteSnapshot>> snapshots = new ConcurrentHashMap<>();
    private final Map<Long, Object> locks = new ConcurrentHashMap<>();

    private static InviteSnapshot toSnapshot(Invite i) {
        return new InviteSnapshot(
                i.getCode(), i.getUses(), i.getMaxUses(),
                i.getInviter() != null ? i.getInviter().getIdLong() : null);
    }

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        refresh(event.getGuild());
    }

    @Override
    public void onGuildInviteCreate(GuildInviteCreateEvent event) {
        snapshots.computeIfAbsent(event.getGuild().getIdLong(), k -> new ConcurrentHashMap<>())
                .put(event.getCode(), toSnapshot(event.getInvite()));
    }

    // Deliberately NOT removing on delete: a max-use invite is deleted by Discord
    // right as the member joins, and we need its last snapshot to detect it.

    private void refresh(Guild guild) {
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_SERVER)) return;
        guild.retrieveInvites().queue(invites -> {
            Map<String, InviteSnapshot> fresh = new ConcurrentHashMap<>();
            invites.forEach(i -> fresh.put(i.getCode(), toSnapshot(i)));
            snapshots.put(guild.getIdLong(), fresh);
        });
    }

    /**
     * Resolves which invite was used for this join. Callback gets null if unknown.
     */
    public void resolveInvite(Guild guild, Consumer<InviteSource> callback) {
        if (!guild.getSelfMember().hasPermission(Permission.MANAGE_SERVER)) {
            callback.accept(null);
            return;
        }

        guild.retrieveInvites().queue(invites -> {
            Object lock = locks.computeIfAbsent(guild.getIdLong(), k -> new Object());
            InviteSource result = null;

            synchronized (lock) {
                Map<String, InviteSnapshot> old =
                        snapshots.getOrDefault(guild.getIdLong(), Map.of());
                Map<String, InviteSnapshot> fresh = new ConcurrentHashMap<>();
                invites.forEach(i -> fresh.put(i.getCode(), toSnapshot(i)));

                // 1) An existing invite whose use count went up
                for (InviteSnapshot now : fresh.values()) {
                    InviteSnapshot before = old.get(now.code());
                    int prev = before == null ? 0 : before.uses();
                    if (now.uses() > prev) {
                        result = new InviteSource(now.code(), now.inviterId(), now.uses());
                        break;
                    }
                }

                // 2) An invite that vanished because its last use was just consumed
                if (result == null) {
                    for (InviteSnapshot before : old.values()) {
                        if (!fresh.containsKey(before.code())
                                && before.maxUses() > 0
                                && before.uses() + 1 >= before.maxUses()) {
                            result = new InviteSource(before.code(), before.inviterId(), before.maxUses());
                            break;
                        }
                    }
                }

                snapshots.put(guild.getIdLong(), fresh);
            }
            callback.accept(result);
        }, err -> callback.accept(null));
    }
}