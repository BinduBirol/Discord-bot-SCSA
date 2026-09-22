package com.scsabot.discordbot.moderation;

import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);

    public void recordAction(Member member, Member moderator, String actionType, String reason, String metadata) {
        log.info(
                "Moderation action {} on {} ({}) by {} ({}): {} {}",
                actionType,
                member != null ? member.getEffectiveName() : "unknown",
                member != null ? member.getId() : "unknown",
                moderator != null ? moderator.getEffectiveName() : "unknown",
                moderator != null ? moderator.getId() : "unknown",
                reason,
                metadata != null ? metadata : ""
        );
    }
}
