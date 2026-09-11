package com.scsabot.discordbot.discord.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EventInterestService {

    private final Map<Long, Set<String>> interestedUsers =
            new ConcurrentHashMap<>();

    /**
     * Toggles a user's interest for an event.
     *
     * @return true  -> user is now interested
     *         false -> user is now not interested
     */
    public boolean toggleInterest(Long eventId, String userId) {

        Set<String> users = interestedUsers.computeIfAbsent(
                eventId,
                id -> ConcurrentHashMap.newKeySet()
        );

        if (users.remove(userId)) {
            return false;
        }

        users.add(userId);
        return true;
    }

    public boolean isInterested(Long eventId, String userId) {

        return interestedUsers
                .getOrDefault(eventId, Set.of())
                .contains(userId);
    }

    public Set<String> getInterestedUsers(Long eventId) {

        return interestedUsers.getOrDefault(
                eventId,
                Set.of()
        );
    }

    public int getInterestedCount(Long eventId) {

        return interestedUsers
                .getOrDefault(eventId, Set.of())
                .size();
    }
}