# Discord Bot SCSA

Minimal Spring Boot + JDA + PostgreSQL foundation for a Discord moderation and community bot.

## Tech stack

- Java 25
- Spring Boot 4.x
- Maven
- JDA
- PostgreSQL
- Spring Data JPA / Hibernate
- Flyway
- Docker / Docker Compose
- JUnit 5

## Project structure

- `config` – Spring and JDA configuration
- `discord` – Discord listeners and command handling
- `service` – application services
- `entity` – JPA entities
- `repository` – Spring Data repositories
- `moderation` – moderation abstractions
- `voice` – voice-session tracking
- `member` – member/profile concepts
- `dto` – DTOs for future expansion
- `common` – shared utilities and enums

## Prerequisites

- Java 25
- Maven
- Docker and Docker Compose
- A Discord bot token

## Local PostgreSQL

Start PostgreSQL:

```bash
docker compose up -d
```

## Environment variables

Create a `.env` file from the example:

```bash
cp .env.example .env
```

Then set values:

```env
DISCORD_BOT_TOKEN=your_discord_bot_token
DISCORD_GUILD_ID=your_discord_guild_id
DATABASE_URL=jdbc:postgresql://localhost:5432/discord_bot
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres
```

## Run the app

```bash
mvn spring-boot:run
```

## Included features in the skeleton

- Discord bot startup and JDA connection
- `/ping` slash command
- `/rules` slash command
- member join/leave persistence
- voice join/leave tracking
- voice session persistence
- basic moderation service abstraction
- Flyway database migrations

## Database schema

The initial schema includes:

- `discord_member`
- `voice_session`
- `moderation_action`
- `audit_log_entry`

Schema changes are managed with Flyway migrations in `src/main/resources/db/migration`.

## Notes

This is intentionally a minimal foundation. The goal is to support future features such as warnings, moderation commands, role-based onboarding, and community analytics without reworking the architecture.
