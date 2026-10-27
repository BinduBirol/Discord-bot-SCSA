
package com.scsabot.discordbot.language;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class LanguageSetupSubcommand {

    private final LanguagePanelService panels;
    private final LanguageRoleService roles;

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

        event.deferReply(true).queue(hook ->
                hook.editOriginal(
                        panels.personalView(
                                guild,
                                roles.memberLanguageKeys(member),
                                0,
                                "Select the languages you speak. " +
                                        "You can add or remove languages anytime."
                        )
                ).queue(
                        success -> log.info(
                                "Language selection opened for user={}",
                                member.getId()
                        ),
                        error -> log.error(
                                "Failed to open language selection",
                                error
                        )
                )
        );
    }
}
