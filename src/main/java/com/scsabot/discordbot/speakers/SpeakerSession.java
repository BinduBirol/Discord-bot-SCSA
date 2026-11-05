package com.scsabot.discordbot.speakers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * State of one speaker list message: who is speaking now, who is waiting,
 * and who has finished. All methods are synchronized because several
 * button clicks can arrive at the same time.
 */
public class SpeakerSession {

    private static final Duration MAX_AGE = Duration.ofHours(24);

    private final long voiceChannelId;
    private final long hostId;
    private final Instant createdAt = Instant.now();

    private Long current;
    private final LinkedList<Long> queue = new LinkedList<>();
    private final List<Long> completed = new ArrayList<>();
    /** Users who pressed "Remove Me" - Refresh must not silently add them back. */
    private final Set<Long> declined = new HashSet<>();

    public SpeakerSession(long voiceChannelId, long hostId) {
        this.voiceChannelId = voiceChannelId;
        this.hostId = hostId;
    }

    public long getVoiceChannelId() {
        return voiceChannelId;
    }

    public long getHostId() {
        return hostId;
    }

    public synchronized boolean isExpired() {
        return createdAt.isBefore(Instant.now().minus(MAX_AGE));
    }

    public synchronized Long getCurrent() {
        return current;
    }

    public synchronized List<Long> getQueue() {
        return new ArrayList<>(queue);
    }

    public synchronized List<Long> getCompleted() {
        return new ArrayList<>(completed);
    }

    public synchronized boolean isSpeakingOrWaiting(long userId) {
        return (current != null && current == userId) || queue.contains(userId);
    }

    private boolean isTracked(long userId) {
        return isSpeakingOrWaiting(userId) || completed.contains(userId);
    }

    /** Adds a user to the end of the queue. Returns false if already speaking/waiting. */
    public synchronized boolean join(long userId) {
        if (isSpeakingOrWaiting(userId)) {
            return false;
        }
        completed.remove(Long.valueOf(userId)); // a finished speaker may queue up again
        declined.remove(userId);
        queue.addLast(userId);
        return true;
    }

    /** Removes a user from the list entirely. Returns false if they were not on it. */
    public synchronized boolean leave(long userId) {
        boolean removed = queue.remove(Long.valueOf(userId));
        removed |= completed.remove(Long.valueOf(userId));

        if (current != null && current == userId) {
            current = queue.pollFirst(); // next person steps up
            removed = true;
        }

        if (removed) {
            declined.add(userId);
        }
        return removed;
    }

    /** Adds voice-channel members who are not on the list yet. Returns how many were added. */
    public synchronized int addNew(List<Long> userIds) {
        int added = 0;
        for (Long userId : userIds) {
            if (!isTracked(userId) && !declined.contains(userId)) {
                queue.addLast(userId);
                added++;
            }
        }
        return added;
    }

    /** Whether pressing "Next Speaker" would do anything. */
    public synchronized boolean canAdvance() {
        return current != null || !queue.isEmpty();
    }

    /** Current speaker (if any) moves to completed; the next in line starts speaking. */
    public synchronized void next() {
        if (current != null) {
            completed.add(current);
        }
        current = queue.pollFirst();
    }

    public synchronized void clear() {
        current = null;
        queue.clear();
        completed.clear();
        declined.clear();
    }
}
