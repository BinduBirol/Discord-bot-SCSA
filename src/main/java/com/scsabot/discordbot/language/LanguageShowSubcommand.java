
package com.scsabot.discordbot.language;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class LanguageShowSubcommand {

    public static final String ID_SETUP = "lang:setup";

    private final LanguageRoleService roles;

    public void handleShowCommand(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();

        if (guild == null || member == null) {
            event.reply("❌ Server only.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Set<String> mine = roles.memberLanguageKeys(member);

        String badges = roles.availableLanguages(guild)
                .stream()
                .filter(name -> mine.contains(name.toLowerCase(Locale.ROOT)))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> "` " + name + " `")
                .collect(Collectors.joining("  "));

        if (badges.isEmpty()) {
            badges = "*No languages selected yet.*";
        }

        event.replyEmbeds(new EmbedBuilder()
                        .setColor(new Color(88, 101, 242))
                        .setAuthor(
                                member.getEffectiveName() + "'s Spoken Languages",
                                null,
                                member.getEffectiveAvatarUrl()
                        )
                        .setDescription(badges)
                        .build())
                .addActionRow(
                        Button.primary(ID_SETUP, "Manage Languages")
                )
                .queue();

        log.info("Language profile shown: user={}", member.getId());
    }
}
