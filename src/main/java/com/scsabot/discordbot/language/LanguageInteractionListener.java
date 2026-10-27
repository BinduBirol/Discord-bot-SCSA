
package com.scsabot.discordbot.language;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletionException;

@Slf4j
@Component
@RequiredArgsConstructor
public class LanguageInteractionListener extends ListenerAdapter {

    private final LanguageRoleService roles;
    private final LanguagePanelService panels;

    // ---------------------------------------------------------
    // LANGUAGE SELECTION
    // ---------------------------------------------------------

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String id = event.getComponentId();

        if (!id.startsWith(LanguagePanelService.ID_SELECT)) return;

        Guild guild = event.getGuild();
        Member member = event.getMember();

        if (guild == null || member == null) {
            event.reply("This feature is only available in a server.")
                    .setEphemeral(true).queue();
            return;
        }

        int page = parsePage(
                id.substring(LanguagePanelService.ID_SELECT.length())
        );

        List<String> pageNames = event.getComponent()
                .getOptions()
                .stream()
                .map(SelectOption::getValue)
                .toList();

        List<String> selected = event.getValues();

        log.info("Language selection: user={}, values={}",
                member.getId(), selected);

        if (event.getMessage().isEphemeral()) {
            event.deferEdit().queue(hook ->
                    applySelection(hook, guild, member, pageNames, selected, page)
            );
        } else {
            event.deferReply(true).queue(hook ->
                    applySelection(hook, guild, member, pageNames, selected, page)
            );
        }
    }

    private void applySelection(
            InteractionHook hook,
            Guild guild,
            Member member,
            List<String> pageNames,
            List<String> selected,
            int page
    ) {
        roles.applyPageSelection(member, pageNames, selected)
                .thenAccept(keys -> {
                    log.info("Languages updated: user={}, languages={}",
                            member.getId(), keys);

                    hook.editOriginal(
                            panels.personalView(
                                    guild,
                                    keys,
                                    page,
                                    "✅ Languages updated."
                            )
                    ).queue();
                })
                .exceptionally(ex -> {
                    fail(hook, ex);
                    return null;
                });
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
            event.reply("This feature is only available in a server.")
                    .setEphemeral(true).queue();
            return;
        }

        log.info("Language button: user={}, id={}",
                member.getId(), id);

        // Manage Languages button from /language show
        if (id.equals(LanguageShowSubcommand.ID_SETUP)) {
            event.deferEdit().queue(hook ->
                    hook.editOriginal(
                            panels.personalView(
                                    guild,
                                    roles.memberLanguageKeys(member),
                                    0,
                                    "Select the languages you speak."
                            )
                    ).queue()
            );
            return;
        }

        // Add custom language
        if (id.equals(LanguagePanelService.ID_ADD)) {
            event.replyModal(panels.addLanguageModal()).queue();
            return;
        }

        // My languages
        if (id.equals(LanguagePanelService.ID_MINE)) {
            event.deferReply(true).queue(hook ->
                    hook.editOriginal(
                            panels.personalView(
                                    guild,
                                    roles.memberLanguageKeys(member),
                                    0,
                                    null
                            )
                    ).queue()
            );
            return;
        }

        // Pagination
        if (id.startsWith(LanguagePanelService.ID_PAGE)) {
            int page = parsePage(
                    id.substring(LanguagePanelService.ID_PAGE.length())
            );

            if (event.getMessage().isEphemeral()) {
                event.deferEdit().queue(hook ->
                        hook.editOriginal(
                                panels.personalView(
                                        guild,
                                        roles.memberLanguageKeys(member),
                                        page,
                                        null
                                )
                        ).queue()
                );
            } else {
                event.deferReply(true).queue(hook ->
                        hook.editOriginal(
                                panels.personalView(
                                        guild,
                                        roles.memberLanguageKeys(member),
                                        page,
                                        null
                                )
                        ).queue()
                );
            }
            return;
        }

        // No-op button
        if (id.equals(LanguagePanelService.ID_NOOP)) {
            event.deferEdit().queue();
            return;
        }

        event.reply("Unknown language action.")
                .setEphemeral(true).queue();
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
            event.reply("This feature is only available in a server.")
                    .setEphemeral(true).queue();
            return;
        }

        ModalMapping input = event.getValue(
                LanguagePanelService.INPUT_NAME
        );

        String name = LanguageRoleService.normalize(
                input == null ? null : input.getAsString()
        );

        event.deferReply(true).queue(hook -> {
            if (name == null) {
                hook.editOriginal(
                        "❌ Invalid language name. Use 2–40 letters. " +
                                "Spaces, hyphens and apostrophes are allowed."
                ).queue();
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
                            message = "ℹ️ You already have **" +
                                    result.display + "** selected.";
                        } else {
                            message = "✅ Added **" +
                                    result.display + "** to your languages.";
                        }

                        hook.editOriginal(message).queue();

                        log.info("Language added: user={}, language={}",
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

    private static int parsePage(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void fail(InteractionHook hook, Throwable error) {
        Throwable cause = error;

        while (cause instanceof CompletionException
                && cause.getCause() != null) {
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
