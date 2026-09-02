package com.scsabot.discordbot.repository;

import com.scsabot.discordbot.entity.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, Long> {
}
