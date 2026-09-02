package com.scsabot.discordbot.voice;

public interface VoiceTrackingService {

    void trackJoin(String guildId, String userId, String channelId);

    void trackLeave(String guildId, String userId);
}
