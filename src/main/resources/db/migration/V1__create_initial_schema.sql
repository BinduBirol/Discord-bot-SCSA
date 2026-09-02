CREATE TABLE IF NOT EXISTS discord_member (
    id BIGSERIAL PRIMARY KEY,
    discord_user_id VARCHAR(255) NOT NULL UNIQUE,
    guild_id VARCHAR(255) NOT NULL,
    username VARCHAR(255),
    display_name VARCHAR(255),
    country VARCHAR(255),
    region VARCHAR(255),
    stuttering_level VARCHAR(255),
    joined_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_discord_member_discord_user_id
    ON discord_member (discord_user_id);

CREATE INDEX IF NOT EXISTS idx_discord_member_guild_id
    ON discord_member (guild_id);

CREATE TABLE IF NOT EXISTS voice_session (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    guild_id VARCHAR(255) NOT NULL,
    voice_channel_id VARCHAR(255) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    left_at TIMESTAMPTZ,
    duration_seconds BIGINT,
    CONSTRAINT fk_voice_session_member
        FOREIGN KEY (member_id) REFERENCES discord_member(id)
); 

CREATE INDEX IF NOT EXISTS idx_voice_session_member_id
    ON voice_session (member_id);

CREATE INDEX IF NOT EXISTS idx_voice_session_guild_channel
    ON voice_session (guild_id, voice_channel_id);

CREATE TABLE IF NOT EXISTS moderation_action (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    moderator_id BIGINT,
    action_type VARCHAR(100) NOT NULL,
    reason TEXT,
    metadata TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_moderation_action_member
        FOREIGN KEY (member_id) REFERENCES discord_member(id),
    CONSTRAINT fk_moderation_action_moderator
        FOREIGN KEY (moderator_id) REFERENCES discord_member(id)
);

CREATE INDEX IF NOT EXISTS idx_moderation_action_member_id
    ON moderation_action (member_id);

CREATE INDEX IF NOT EXISTS idx_moderation_action_moderator_id
    ON moderation_action (moderator_id);

CREATE INDEX IF NOT EXISTS idx_moderation_action_created_at
    ON moderation_action (created_at);

CREATE TABLE IF NOT EXISTS audit_log_entry (
    id BIGSERIAL PRIMARY KEY,
    guild_id VARCHAR(255) NOT NULL,
    entity_id VARCHAR(255),
    action VARCHAR(50) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_entry_entity_id
    ON audit_log_entry (entity_id);

CREATE INDEX IF NOT EXISTS idx_audit_log_entry_created_at
    ON audit_log_entry (created_at);
