package com.scsabot.discordbot.language;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * /language setup. Opens the private language picker.
 * The "Manage Languages" button on the /language show card calls {@link #openPicker} too,
 * so the command and the button always behave exactly the same.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LanguageSetupSubcommand {

    private static final String SETUP_NOTE =
            "Select the languages you speak, then press **Save**. You can change them anytime.";

    private final LanguagePanelService panels;
    private final LanguageRoleService roles;
    private final LanguageDraftStore drafts;

    public void handleSetupCommand(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();

        if (guild == null || member == null) {
            event.reply("❌ This command can only be used in a server.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        log.info("Language setup requested by user={}", member.getId());

        event.deferReply(true).queue(hook -> openPicker(hook, member));
    }

    /**
     * Fills the (already deferred, private) reply with the language picker, starting a fresh
     * unsaved draft from the member's saved languages. Used by /language setup and by the
     * "Manage Languages" / "My Languages" buttons.
     */
    public void openPicker(InteractionHook hook, Member member) {
        Set<String> current = roles.memberLanguageKeys(member);
        drafts.set(member, current);

        hook.editOriginal(panels.personalView(member, current, 0, SETUP_NOTE)).queue(
                success -> log.info("Language selection opened for user={}", member.getId()),
                error -> log.error("Failed to open language selection", error)
        );
    }
}