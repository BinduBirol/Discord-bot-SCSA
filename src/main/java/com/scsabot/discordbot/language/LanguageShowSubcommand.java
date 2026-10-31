package com.scsabot.discordbot.language;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * /language show. Also builds the public card that is posted after someone saves.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class LanguageShowSubcommand {

    public static final String ID_SETUP = "lang:setup";

    private static final Color COLOR = new Color(88, 101, 242);

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

        event.reply(profileMessage(guild, member, roles.memberLanguageKeys(member))).queue();

        log.info("Language profile shown: user={}", member.getId());
    }

    /**
     * Builds the "X's Spoken Languages" card with the Manage Languages button.
     * Shared by /language show and the public post made after someone saves their languages.
     */
    public MessageCreateData profileMessage(Guild guild, Member member, Set<String> languageKeys) {
        String badges = roles.availableLanguages(guild)
                .stream()
                .filter(name -> languageKeys.contains(LanguageRoleService.key(name)))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> "` " + name + " `")
                .collect(Collectors.joining("  "));

        if (badges.isEmpty()) {
            badges = "*No languages selected yet.*";
        }

        return new MessageCreateBuilder()
                .setEmbeds(new EmbedBuilder()
                        .setColor(COLOR)
                        .setAuthor(
                                member.getEffectiveName() + "'s Spoken Languages",
                                null,
                                member.getEffectiveAvatarUrl()
                        )
                        .setDescription(badges)
                        .build())
                .setComponents(ActionRow.of(Button.primary(ID_SETUP, "Manage Languages")))
                .build();
    }
}