package com.scsabot.discordbot.moderation;

import com.scsabot.discordbot.entity.DiscordMember;
import com.scsabot.discordbot.entity.ModerationAction;
import com.scsabot.discordbot.repository.ModerationActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModerationService {

    private final ModerationActionRepository moderationActionRepository;

    public ModerationService(ModerationActionRepository moderationActionRepository) {
        this.moderationActionRepository = moderationActionRepository;
    }

    @Transactional
    public ModerationAction recordAction(DiscordMember member, DiscordMember moderator, String actionType, String reason, String metadata) {
        ModerationAction action = new ModerationAction(member, moderator, actionType, reason, metadata);
        return moderationActionRepository.save(action);
    }
}
