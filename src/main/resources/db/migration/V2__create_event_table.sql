CREATE TABLE IF NOT EXISTS event (
    id BIGSERIAL PRIMARY KEY,
    guild_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    event_time TIMESTAMPTZ NOT NULL,
    event_channel_id VARCHAR(255) NOT NULL,
    post_channel_id VARCHAR(255) NOT NULL,
    post_message_id VARCHAR(255),
    reminder_24h_sent BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_1h_sent BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_15m_sent BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_start_sent BOOLEAN NOT NULL DEFAULT FALSE,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_event_guild_id
    ON event (guild_id);

CREATE INDEX IF NOT EXISTS idx_event_event_time
    ON event (event_time);

CREATE INDEX IF NOT EXISTS idx_event_created_at
    ON event (created_at);
