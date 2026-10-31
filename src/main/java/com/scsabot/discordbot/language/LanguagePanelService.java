package com.scsabot.discordbot.language;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Builds the persistent panel and ephemeral views, and manages the single panel message.
 */
@Service
public class LanguagePanelService {

    // Persistent component IDs (stateless; survive restarts).
    public static final String ID_SELECT = "lang:select:";   // + page index
    public static final String ID_PAGE = "lang:page:";       // + target page index
    public static final String ID_MINE = "lang:mine";
    public static final String ID_ADD = "lang:add";
    public static final String ID_SAVE = "lang:save";
    public static final String ID_NOOP = "lang:noop";
    public static final String ID_MODAL = "lang:modal";
    public static final String INPUT_NAME = "name";

    static final int PAGE_SIZE = 25;
    private static final int MAX_LISTED_LANGUAGES = 40;
    private static final Color COLOR = new Color(0x5865F2);
    private static final Color COLOR_UNSAVED = new Color(0xFAA61A);

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

    /**
     * Public panel message with the first page of languages.
     */
    public MessageCreateData panelMessage(Guild guild) {
        List<String> all = roles.availableLanguages(guild);
        List<String> first = all.subList(0, Math.min(PAGE_SIZE, all.size()));
        boolean paged = all.size() > PAGE_SIZE;

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(COLOR)
                .setTitle("🗣️ Spoken Languages")
                .setDescription("Pick the languages you speak. You can choose several.\n\n"
                        + "• Use the dropdown to select languages.\n"
                        + "• **My Languages** shows your selections" + (paged ? " and every language (paged)" : "") + ".\n"
                        + "• **Add Language** adds a new one for everyone.");
        if (paged) {
            eb.setFooter("Showing the first " + PAGE_SIZE + " of " + all.size()
                    + " languages. Use “My Languages” to browse all.");
        }

        return new MessageCreateBuilder()
                .setEmbeds(eb.build())
                .setComponents(
                        ActionRow.of(menu(ID_SELECT + 0, first, Set.of(), "Select your spoken languages")),
                        ActionRow.of(
                                Button.primary(ID_MINE, "My Languages"),
                                Button.secondary(ID_ADD, "Add Language")))
                .build();
    }

    /**
     * Personalised ephemeral picker with the member's (unsaved) selections pre-ticked on the
     * given page. When the selection differs from the member's saved languages, the embed turns
     * orange, shows an "unsaved changes" line and the Save button turns green; otherwise Save is
     * grey and disabled.
     */
    public MessageEditData personalView(Member member, Set<String> selectedKeys, int page, String note) {
        Guild guild = member.getGuild();
        boolean dirty = !selectedKeys.equals(roles.memberLanguageKeys(member));
        List<String> all = roles.availableLanguages(guild);
        int pages = Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));
        List<String> slice = all.subList(page * PAGE_SIZE, Math.min(all.size(), (page + 1) * PAGE_SIZE));

        String body = "**Selected:** " + selectedText(all, selectedKeys, "_none yet_");
        if (dirty) {
            body += "\n\n> ⚠️ **Unsaved changes!** Press **💾 Save changes** to apply them.";
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(dirty ? COLOR_UNSAVED : COLOR)
                .setTitle("🗣️ Your Languages")
                .setDescription(withNote(note, body))
                .setFooter("Page " + (page + 1) + " of " + pages
                        + (dirty ? " • ⚠️ Unsaved changes" : " • Pick languages, then press Save"));

        Button prev = Button.secondary(ID_PAGE + (page - 1), "◀ Prev").withDisabled(page == 0);
        Button label = Button.secondary(ID_NOOP, "Page " + (page + 1) + "/" + pages).asDisabled();
        Button next = Button.secondary(ID_PAGE + (page + 1), "Next ▶").withDisabled(page >= pages - 1);

        return new MessageEditBuilder()
                .setContent("")   // clears any earlier error text
                .setEmbeds(eb.build())
                .setComponents(
                        ActionRow.of(menu(ID_SELECT + page, slice, selectedKeys,
                                "Choose languages (page " + (page + 1) + ")")),
                        ActionRow.of(prev, label, next),
                        ActionRow.of(
                                Button.secondary(ID_ADD, "Add Language"),
                                dirty
                                        ? Button.success(ID_SAVE, "💾 Save changes")
                                        : Button.secondary(ID_SAVE, "💾 Save").asDisabled()))
                .build();
    }

    /**
     * Read-only summary of the user's languages (shown after saving).
     */
    public MessageEditData showView(Guild guild, Set<String> selectedKeys, String note) {
        List<String> all = roles.availableLanguages(guild);

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(COLOR)
                .setTitle("🗣️ Your Languages")
                .setDescription(withNote(note,
                        "**Selected:** " + selectedText(all, selectedKeys, "_You haven't selected any languages yet._")));

        return new MessageEditBuilder()
                .setContent("")             // clears any earlier error text
                .setEmbeds(eb.build())
                .setComponents(List.of())   // clears the dropdown and buttons
                .build();
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

    private StringSelectMenu menu(String id, List<String> names, Set<String> selectedKeys, String placeholder) {
        StringSelectMenu.Builder builder = StringSelectMenu.create(id)
                .setPlaceholder(placeholder)
                .setRequiredRange(0, Math.max(1, names.size()));
        for (String name : names) {
            builder.addOptions(SelectOption.of(name, name)
                    .withDefault(selectedKeys.contains(LanguageRoleService.key(name))));
        }
        return builder.build();
    }

    private static String selectedText(List<String> all, Set<String> selectedKeys, String emptyText) {
        List<String> mine = all.stream()
                .filter(name -> selectedKeys.contains(LanguageRoleService.key(name)))
                .collect(Collectors.toList());
        if (mine.isEmpty()) {
            return emptyText;
        }
        String listed = mine.stream().limit(MAX_LISTED_LANGUAGES).collect(Collectors.joining(", "));
        return mine.size() > MAX_LISTED_LANGUAGES
                ? listed + " +" + (mine.size() - MAX_LISTED_LANGUAGES) + " more"
                : listed;
    }

    private static String withNote(String note, String body) {
        return note == null || note.isEmpty() ? body : note + "\n\n" + body;
    }

    // ---------------------------------------------------------- panel mgmt

    /**
     * Creates the panel, or updates the existing one. Never posts a duplicate.
     */
    public CompletableFuture<SetupResult> setupPanel(Guild guild, GuildMessageChannel target) {
        long guildId = guild.getIdLong();
        if (!setupInFlight.add(guildId)) {
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
                return target.sendMessage(data).submit()
                        .thenApply(m -> remember(m, false));
            });
        } catch (RuntimeException e) {
            setupInFlight.remove(guildId);
            return CompletableFuture.failedFuture(e);
        }

        future.whenComplete((result, error) -> setupInFlight.remove(guildId));
        return future;
    }

    private CompletableFuture<Message> locateExisting(Guild guild, GuildMessageChannel target) {
        long guildId = guild.getIdLong();
        PanelLocation loc = panels.get(guildId);

        if (loc != null) {
            GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, loc.channelId);
            if (channel != null) {
                return channel.retrieveMessageById(loc.messageId).submit()
                        .handle((message, error) -> error == null ? message : null)
                        .thenCompose(message -> {
                            if (message != null) {
                                return CompletableFuture.completedFuture(message);
                            }
                            panels.remove(guildId);
                            return scan(target);
                        });
            }
            panels.remove(guildId);
        }
        return scan(target);
    }

    private CompletableFuture<Message> scan(GuildMessageChannel channel) {
        return channel.getHistory().retrievePast(100).submit()
                .thenApply(list -> list.stream().filter(this::isPanel).findFirst().orElse(null));
    }

    private boolean isPanel(Message message) {
        return message.getAuthor().getIdLong() == message.getJDA().getSelfUser().getIdLong()
                && hasButton(message, ID_ADD)
                && hasButton(message, ID_MINE);
    }

    private static boolean hasButton(Message message, String id) {
        return message.getButtons().stream().anyMatch(b -> id.equals(b.getId()));
    }

    private SetupResult remember(Message message, boolean updated) {
        registerPanel(message);
        return new SetupResult(updated, message.getJumpUrl());
    }

    /**
     * Called when someone interacts with the public panel; recovers the cache after a restart.
     */
    public void registerPanel(Message message) {
        if (message.isFromGuild()) {
            panels.put(message.getGuildIdLong(),
                    new PanelLocation(message.getChannelIdLong(), message.getIdLong()));
        }
    }

    /**
     * Re-renders the panel so newly added languages show up in its dropdown.
     */
    public void refreshPanel(Guild guild) {
        long guildId = guild.getIdLong();
        PanelLocation loc = panels.get(guildId);
        if (loc == null) return;

        GuildMessageChannel channel = guild.getChannelById(GuildMessageChannel.class, loc.channelId);
        if (channel == null) {
            panels.remove(guildId);
            return;
        }

        channel.editMessageById(loc.messageId, MessageEditData.fromCreateData(panelMessage(guild)))
                .queue(null, error -> {
                    if (error instanceof ErrorResponseException
                            && ((ErrorResponseException) error).getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                        panels.remove(guildId);
                    }
                });
    }
}