package com.scsabot.discordbot.breathing;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.nio.ByteBuffer;

/**
 * Bridges a LavaPlayer {@link AudioPlayer} to JDA's audio sending pipeline.
 * LavaPlayer already produces Opus frames, so JDA does not need to re-encode.
 */
final class BreathingRoomAudioSendHandler implements AudioSendHandler {

    private final AudioPlayer audioPlayer;
    private final ByteBuffer buffer = ByteBuffer.allocate(1024);
    private final MutableAudioFrame frame = new MutableAudioFrame();

    BreathingRoomAudioSendHandler(AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
        this.frame.setBuffer(buffer);
    }

    @Override
    public boolean canProvide() {
        return audioPlayer.provide(frame);
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        return buffer.flip();
    }

    @Override
    public boolean isOpus() {
        return true;
    }
}
