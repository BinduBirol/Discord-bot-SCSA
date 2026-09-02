package com.scsabot.discordbot.repository;

import com.scsabot.discordbot.entity.ModerationAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {
}
