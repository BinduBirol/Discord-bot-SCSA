# Discord Bot SCSA

Minimal Spring Boot + JDA foundation for a Discord moderation and community bot.

Discord is the single source of truth for Discord-related data. The bot talks to Discord through JDA and does not persist guild, member, role, or event data in a database.

## Tech stack

- Java 21
- Spring Boot 4.x
- Maven
- JDA
- Docker / Docker Compose
- JUnit 5

## Project structure

- `config` – Spring and JDA configuration
- `discord` – Discord listeners and command handling
- `service` – application services
- `moderation` – moderation abstractions
- `voice` – in-memory voice-session tracking for activity logs
- `member` – member/profile concepts
- `dto` – DTOs for future expansion
- `common` – shared utilities and enums

## Prerequisites

- Java 21
- Maven
- A Discord bot token

## Environment variables

Create a `.env` file from the example:

```bash
cp .env.example .env
```

Then set values:

```env
DISCORD_BOT_TOKEN=your_discord_bot_token
DISCORD_GUILD_ID=your_discord_guild_id
```

## Run the app

```bash
mvn spring-boot:run
```

Or with Docker:

```bash
docker compose up --build
```

## Included features

- Discord bot startup and JDA connection
- `/ping` slash command
- `/rules` slash command
- `/event` commands backed by Discord Scheduled Events
- member join/leave welcome and activity messages
- voice join/leave activity logging
- basic moderation service abstraction
