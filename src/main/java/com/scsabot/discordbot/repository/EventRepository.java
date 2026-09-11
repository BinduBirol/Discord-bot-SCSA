package com.scsabot.discordbot.repository;

import com.scsabot.discordbot.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByGuildId(String guildId);

    @Query("SELECT e FROM Event e WHERE e.guildId = :guildId AND e.eventTime >= :startTime AND e.eventTime < :endTime ORDER BY e.eventTime ASC")
    List<Event> findUpcomingEvents(@Param("guildId") String guildId, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

    @Query("SELECT e FROM Event e WHERE e.eventTime > :now ORDER BY e.eventTime ASC")
    List<Event> findFutureEvents(@Param("now") Instant now);

    Optional<Event> findByIdAndGuildId(Long id, String guildId);
}
