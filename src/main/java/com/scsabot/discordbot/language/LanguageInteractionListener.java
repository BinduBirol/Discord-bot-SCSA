package com.scsabot.discordbot.language;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Handles the language picker's dropdown, buttons and "Add Language" modal.
 * Selecting only changes an unsaved draft; roles change when the member presses Save.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LanguageInteractionListener extends ListenerAdapter {

    private static final String SERVER_ONLY_MESSAGE = "This feature is only available in a server.";
    private static final String INVALID_NAME_MESSAGE =
            "❌ Invalid language name. Use 2–40 letters. Spaces, hyphens and apostrophes are allowed.";

    private final LanguageRoleService roles;
    private final LanguagePanelService panels;
    private final LanguageShowSubcommand show;
    private final LanguageSetupSubcommand setup;
    private final LanguageDraftStore drafts;

    // ---------------------------------------------------------
    // LANGUAGE SELECTION (only updates the unsaved draft)
    // ---------------------------------------------------------

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String id = event.getComponentId();

        if (!id.startsWith(LanguagePanelService.ID_SELECT)) return;

        Guild guild = event.getGuild();
        Member member = event.getMember();

        if (guild == null || member == null) {
            event.reply(SERVER_ONLY_MESSAGE).setEphemeral(true).queue();
            return;
        }

        int page = parsePage(id.substring(LanguagePanelService.ID_SELECT.length()));

        Set<String> pageKeys = event.getComponent()
                .getOptions()
                .stream()
                .map(SelectOption::getValue)
                .map(LanguageRoleService::key)
                .collect(Collectors.toSet());

        // Replace this page's part of the draft with what is selected now.
        Set<String> draft = updateDraft(member, d -> {
            d.removeAll(pageKeys);
            for (String value : event.getValues()) {
                String k = LanguageRoleService.key(value);
                if (pageKeys.contains(k)) d.add(k);
            }
        });

        log.info("Language draft updated: user={}, draft={}", member.getId(), draft);

        updatePicker(event, panels.personalView(member, draft, page, null));
    }

    // ---------------------------------------------------------
    // BUTTONS
    // ---------------------------------------------------------

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();

        if (!id.startsWith("lang:")) return;

        Guild guild = event.getGuild();
        Member member = event.getMember();

        if (guild == null || member == null) {
            event.reply(SERVER_ONLY_MESSAGE).setEphemeral(true).queue();
            return;
        }

        log.info("Language button: user={}, id={}", member.getId(), id);

        // Save: the only place roles change from the picker
        if (id.equals(LanguagePanelService.ID_SAVE)) {
            event.deferEdit().queue(hook -> saveDraft(hook, event.getChannel(), guild, member));
            return;
        }

        // Manage Languages (on the /language show card) and My Languages (public panel):
        // exactly the same as /language setup
        if (id.equals(LanguageShowSubcommand.ID_SETUP) || id.equals(LanguagePanelService.ID_MINE)) {
            event.deferReply(true).queue(hook -> setup.openPicker(hook, member));
            return;
        }

        // Add custom language
        if (id.equals(LanguagePanelService.ID_ADD)) {
            event.replyModal(panels.addLanguageModal()).queue();
            return;
        }

        // Pagination (keeps the unsaved draft)
        if (id.startsWith(LanguagePanelService.ID_PAGE)) {
            int page = parsePage(id.substring(LanguagePanelService.ID_PAGE.length()));
            updatePicker(event, panels.personalView(member, currentDraft(member), page, null));
            return;
        }

        // No-op button
        if (id.equals(LanguagePanelService.ID_NOOP)) {
            event.deferEdit().queue();
            return;
        }

        event.reply("Unknown language action.").setEphemeral(true).queue();
    }

    // ---------------------------------------------------------
    // SAVE
    // ---------------------------------------------------------

    private void saveDraft(InteractionHook hook, MessageChannel channel, Guild guild, Member member) {
        Set<String> draft = drafts.get(member);

        if (draft == null) {
            hook.editOriginal(panels.showView(
                    guild,
                    roles.memberLanguageKeys(member),
                    "⚠️ Your selection expired, so nothing was changed. Please open the picker again."
            )).queue();
            return;
        }

        Set<String> before = roles.memberLanguageKeys(member);

        roles.applySelection(member, draft)
                .thenAccept(keys -> {
                    drafts.clear(member);
                    log.info("Languages saved: user={}, languages={}", member.getId(), keys);

                    // Private: the picker becomes a short confirmation.
                    hook.editOriginal(panels.showView(guild, keys, "✅ Languages saved.")).queue();

                    // Public: same card as /language show, only if something changed.
                    if (!keys.equals(before)) {
                        channel.sendMessage(show.profileMessage(guild, member, keys)).queue(
                                success -> {
                                },
                                ex -> log.warn("Couldn't post public language card: {}", ex.getMessage())
                        );
                    }
                })
                .exceptionally(ex -> {
                    fail(hook, ex);
                    return null;
                });
    }

    // ---------------------------------------------------------
    // ADD LANGUAGE MODAL
    // ---------------------------------------------------------

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().equals(LanguagePanelService.ID_MODAL)) {
            return;
        }

        Guild guild = event.getGuild();
        Member member = event.getMember();

        if (guild == null || member == null) {
            event.reply(SERVER_ONLY_MESSAGE).setEphemeral(true).queue();
            return;
        }

        ModalMapping input = event.getValue(LanguagePanelService.INPUT_NAME);
        String name = LanguageRoleService.normalize(input == null ? null : input.getAsString());

        // Opened from the private picker: add to the unsaved draft and refresh the picker.
        Message origin = event.getMessage();
        if (origin != null && origin.isEphemeral()) {
            addLanguageToDraft(event, guild, member, name);
            return;
        }

        // Opened from the public panel: no Save step there, so assign right away.
        event.deferReply(true).queue(hook -> {
            if (name == null) {
                hook.editOriginal(INVALID_NAME_MESSAGE).queue();
                return;
            }

            roles.addLanguage(member, name)
                    .thenAccept(result -> {
                        String message;

                        if (result.newToServer) {
                            message = "✅ **" + result.display +
                                    "** is now available to everyone " +
                                    "and has been added to your languages.";
                        } else if (result.alreadyHad) {
                            message = "ℹ️ You already have **" + result.display + "** selected.";
                        } else {
                            message = "✅ Added **" + result.display + "** to your languages.";
                        }

                        hook.editOriginal(message).queue();

                        log.info("Language added: user={}, language={}", member.getId(), result.display);
                    })
                    .exceptionally(ex -> {
                        fail(hook, ex);
                        return null;
                    });
        });
    }

    /**
     * Picker flow: make sure the language role exists (so it is available to everyone), add it
     * to the member's unsaved selection and jump the picker to the page where it lives.
     * The member only receives the role when they press Save.
     */
    private void addLanguageToDraft(ModalInteractionEvent event, Guild guild, Member member, String name) {
        event.deferEdit().queue(hook -> {
            if (name == null) {
                hook.editOriginal(
                        panels.personalView(member, currentDraft(member), 0, INVALID_NAME_MESSAGE)
                ).queue();
                return;
            }

            roles.addLanguage(member, name, false)
                    .thenAccept(result -> {
                        Set<String> draft = updateDraft(member,
                                d -> d.add(LanguageRoleService.key(result.display)));

                        int index = roles.availableLanguages(guild).indexOf(result.display);
                        int page = index < 0 ? 0 : index / LanguagePanelService.PAGE_SIZE;

                        String note = result.newToServer
                                ? "✅ **" + result.display + "** is now available to everyone and added to your "
                                  + "selection. Press **Save** to apply."
                                : "✅ Added **" + result.display + "** to your selection. Press **Save** to apply.";

                        hook.editOriginal(panels.personalView(member, draft, page, note)).queue();

                        log.info("Language added to draft: user={}, language={}",
                                member.getId(), result.display);
                    })
                    .exceptionally(ex -> {
                        fail(hook, ex);
                        return null;
                    });
        });
    }

    // ---------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------

    /**
     * Private picker: edit it in place. Public message (panel): open a private picker instead.
     */
    private void updatePicker(ComponentInteraction event, MessageEditData view) {
        if (event.getMessage().isEphemeral()) {
            event.editMessage(view).queue();
        } else {
            event.deferReply(true).queue(hook -> hook.editOriginal(view).queue());
        }
    }

    /**
     * The member's unsaved draft, or their saved languages if no draft exists (yet).
     */
    private Set<String> currentDraft(Member member) {
        Set<String> draft = drafts.get(member);
        return draft != null ? draft : roles.memberLanguageKeys(member);
    }

    /**
     * Applies a change to a copy of the member's draft, stores it and returns it.
     */
    private Set<String> updateDraft(Member member, Consumer<Set<String>> change) {
        Set<String> draft = new LinkedHashSet<>(currentDraft(member));
        change.accept(draft);
        drafts.set(member, draft);
        return draft;
    }

    private static int parsePage(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void fail(InteractionHook hook, Throwable error) {
        Throwable cause = error;

        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }

        String message;

        if (cause instanceof IllegalStateException) {
            message = cause.getMessage();
        } else if (cause instanceof HierarchyException
                || cause instanceof InsufficientPermissionException) {
            message = "I couldn't update roles. " +
                    "Please give me **Manage Roles** permission " +
                    "and keep my role above the language roles.";
        } else {
            log.error("Language interaction failed", cause);
            message = "Something went wrong. Please try again.";
        }

        hook.editOriginal("❌ " + message).queue(
                success -> {
                },
                ex -> log.error("Failed to send error response", ex)
        );
    }
}