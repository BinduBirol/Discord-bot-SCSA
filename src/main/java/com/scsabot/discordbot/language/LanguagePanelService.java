package com.scsabot.discordbot.language;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Builds the persistent panel / ephemeral views and manages the single panel message.
 */
@Service
public class LanguagePanelService {

    // Persistent component IDs (stateless; survive restarts).
    public static final String ID_SELECT = "lang:select:";   // + page index
    public static final String ID_PAGE = "lang:page:";       // + target page index
    public static final String ID_MINE = "lang:mine";
    public static final String ID_ADD = "lang:add";
    public static final String ID_NOOP = "lang:noop";
    public static final String ID_MODAL = "lang:modal";
    public static final String INPUT_NAME = "name";

    static final int PAGE_SIZE = 25;
    private static final Color COLOR = new Color(0x5865F2);

    private static final class PanelLocation {
        final long channelId;
        final long messageId;

        PanelLocation(long channelId, long messageId) {
            this.channelId = channelId;
            this.messageId = messageId;
        }
    }

    public static final class SetupResult {
        public final boolean updated;
        public final String jumpUrl;

        SetupResult(boolean updated, String jumpUrl) {
            this.updated = updated;
            this.jumpUrl = jumpUrl;
        }
    }

    private final LanguageRoleService roles;
    /**
     * Known panel location per guild (in-memory cache; recovered by scanning / interactions).
     */
    private final Map<Long, PanelLocation> panels = new ConcurrentHashMap<>();
    private final Set<Long> setupInFlight = ConcurrentHashMap.newKeySet();

    public LanguagePanelService(LanguageRoleService roles) {
        this.roles = roles;
    }

    // ------------------------------------------------------------ builders

    public MessageCreateData panelMessage(Guild guild) {
        List<String> all = roles.availableLanguages(guild);
        List<String> first = all.subList(0, Math.min(PAGE_SIZE, all.size()));

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(COLOR)
                .setTitle("🗣️ Spoken Languages")
                .setDescription("Pick the languages you speak. You can choose several.\n\n"
                        + "• Use the dropdown to select languages.\n"
                        + "• **My Languages** shows your selections" + (all.size() > PAGE_SIZE ? " and every language (paged)" : "") + ".\n"
                        + "• **Add Language** adds a new one for everyone.");
        if (all.size() > PAGE_SIZE) {
            eb.setFooter("Showing the first " + PAGE_SIZE + " of " + all.size() + " languages. Use “My Languages” to browse all.");
        }

        return new MessageCreateBuilder()
                .setEmbeds(eb.build())
                .setComponents(
                        ActionRow.of(menu(ID_SELECT + 0, first, Set.of(), "Select your spoken languages")),
                        ActionRow.of(Button.primary(ID_MINE, "My Languages"),
                                Button.secondary(ID_ADD, "Add Language")))
                .build();
    }

    /**
     * Personalised ephemeral view with the user's selections pre-ticked on the given page.
     */
    public MessageEditData personalView(Guild guild, Set<String> selectedKeys, int page, String note) {
        List<String> all = roles.availableLanguages(guild);
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        List<String> slice = all.subList(page * PAGE_SIZE, Math.min(all.size(), (page + 1) * PAGE_SIZE));

        List<String> mine = all.stream()
                .filter(n -> selectedKeys.contains(LanguageRoleService.key(n)))
                .collect(Collectors.toList());
        String mineText = mine.isEmpty() ? "_none yet_"
                : mine.stream().limit(40).collect(Collectors.joining(", "))
                  + (mine.size() > 40 ? " +" + (mine.size() - 40) + " more" : "");

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(COLOR)
                .setTitle("🗣️ Your Languages")
                .setDescription((note == null || note.isEmpty() ? "" : note + "\n\n")
                        + "**Selected:** " + mineText)
                .setFooter("Page " + (page + 1) + " of " + pages);

        Button prev = Button.secondary(ID_PAGE + (page - 1), "◀ Prev").withDisabled(page == 0);
        Button label = Button.secondary(ID_NOOP, "Page " + (page + 1) + "/" + pages).asDisabled();
        Button next = Button.secondary(ID_PAGE + (page + 1), "Next ▶").withDisabled(page >= pages - 1);

        return new MessageEditBuilder()
                .setEmbeds(eb.build())
                .setComponents(
                        ActionRow.of(menu(ID_SELECT + page, slice, selectedKeys, "Choose languages (page " + (page + 1) + ")")),
                        ActionRow.of(prev, label, next, Button.success(ID_ADD, "Add Language")))
                .build();
    }

    private StringSelectMenu menu(String id, List<String> names, Set<String> selectedKeys, String placeholder) {
        StringSelectMenu.Builder b = StringSelectMenu.create(id)
                .setPlaceholder(placeholder)
                .setRequiredRange(0, Math.max(1, names.size()));
        for (String n : names) {
            b.addOptions(SelectOption.of(n, n)
                    .withDefault(selectedKeys.contains(n.toLowerCase(Locale.ROOT))));
        }
        return b.build();
    }

    public Modal addLanguageModal() {
        TextInput input = TextInput.create(INPUT_NAME, "Language name", TextInputStyle.SHORT)
                .setPlaceholder("e.g. Sylheti")
                .setMinLength(2)
                .setMaxLength(40)
                .setRequired(true)
                .build();
        return Modal.create(ID_MODAL, "Add a Language").addActionRow(input).build();
    }

    // ---------------------------------------------------------- panel mgmt

    /**
     * Creates the panel, or updates the existing one. Never posts a duplicate.
     */
    public CompletableFuture<SetupResult> setupPanel(Guild guild, GuildMessageChannel target) {
        if (!setupInFlight.add(guild.getIdLong())) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("A panel setup is already running for this server."));
        }
        CompletableFuture<SetupResult> future;
        try {
            future = locateExisting(guild, target).thenCompose(existing -> {
                MessageCreateData data = panelMessage(guild);
                if (existing != null) {
                    return existing.editMessage(MessageEditData.fromCreateData(data)).submit()
                            .thenApply(m -> remember(m, true));
                }
                return target.sendMessage(data).submit().thenApply(m -> remember(m, false));
            });
        } catch (RuntimeException e) {
            setupInFlight.remove(guild.getIdLong());
            return CompletableFuture.failedFuture(e);
        }
        future.whenComplete((r, ex) -> setupInFlight.remove(guild.getIdLong()));
        return future;
    }

    private CompletableFuture<Message> locateExisting(Guild guild, GuildMessageChannel target) {
        PanelLocation loc = panels.get(guild.getIdLong());
        if (loc != null) {
            GuildMessageChannel ch = guild.getChannelById(GuildMessageChannel.class, loc.channelId);
            if (ch != null) {
                return ch.retrieveMessageById(loc.messageId).submit()
                        .handle((m, ex) -> ex == null ? m : null)
                        .thenCompose(m -> {
                            if (m != null) return CompletableFuture.completedFuture(m);
                            panels.remove(guild.getIdLong());
                            return scan(target);
                        });
            }
            panels.remove(guild.getIdLong());
        }
        return scan(target);
    }

    private CompletableFuture<Message> scan(GuildMessageChannel channel) {
        return channel.getHistory().retrievePast(100).submit()
                .thenApply(list -> list.stream().filter(this::isPanel).findFirst().orElse(null));
    }

    private boolean isPanel(Message m) {
        return m.getAuthor().getIdLong() == m.getJDA().getSelfUser().getIdLong()
                && m.getButtons().stream().anyMatch(b -> ID_ADD.equals(b.getId()))
                && m.getButtons().stream().anyMatch(b -> ID_MINE.equals(b.getId()));
    }

    private SetupResult remember(Message m, boolean updated) {
        panels.put(m.getGuildIdLong(), new PanelLocation(m.getChannelIdLong(), m.getIdLong()));
        return new SetupResult(updated, m.getJumpUrl());
    }

    /**
     * Called when someone interacts with the public panel; recovers the cache after a restart.
     */
    public void registerPanel(Message m) {
        if (m.isFromGuild()) {
            panels.put(m.getGuildIdLong(), new PanelLocation(m.getChannelIdLong(), m.getIdLong()));
        }
    }

    /**
     * Re-renders the panel so newly added languages show up in its dropdown.
     */
    public void refreshPanel(Guild guild) {
        PanelLocation loc = panels.get(guild.getIdLong());
        if (loc == null) return;
        GuildMessageChannel ch = guild.getChannelById(GuildMessageChannel.class, loc.channelId);
        if (ch == null) {
            panels.remove(guild.getIdLong());
            return;
        }
        ch.editMessageById(loc.messageId, MessageEditData.fromCreateData(panelMessage(guild)))
                .queue(null, ex -> {
                    if (ex instanceof ErrorResponseException
                            && ((ErrorResponseException) ex).getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                        panels.remove(guild.getIdLong());
                    }
                });
    }
}
