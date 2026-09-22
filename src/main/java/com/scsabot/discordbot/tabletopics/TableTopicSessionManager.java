package com.scsabot.discordbot.tabletopics;

import com.scsabot.discordbot.dto.TableTopicSession;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TableTopicSessionManager {

    private final Map<String, TableTopicSession> sessions =
            new ConcurrentHashMap<>();

    public String createSession(TableTopicSession session) {

        String sessionId = UUID.randomUUID().toString();

        sessions.put(sessionId, session);

        return sessionId;
    }

    public TableTopicSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }
}