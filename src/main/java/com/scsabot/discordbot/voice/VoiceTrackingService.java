package com.scsabot.discordbot.voice;

import java.util.Optional;

public interface VoiceTrackingService {

    Optional<ActiveVoiceSession> trackJoin(String guildId, String userId, String channelId);

    Optional<ActiveVoiceSession> trackLeave(String guildId, String userId);
}
