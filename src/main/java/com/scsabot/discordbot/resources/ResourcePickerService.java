package com.scsabot.discordbot.resources;

import com.scsabot.discordbot.dto.ResourceEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hands out one ResourceEntry at a time.
 * When avoidRepeatsUntilExhausted is true (default), entries are served from
 * a shuffled "bag": nothing repeats until every entry has been posted once,
 * then the bag is reshuffled and it starts over.
 */
@RequiredArgsConstructor
@Component
public class ResourcePickerService {

    private final ResourceRepository repository;
    private final ResourceBotProperties properties;

    private final List<ResourceEntry> shuffleBag = new ArrayList<>();

    public synchronized ResourceEntry next() {
        List<ResourceEntry> all = repository.getEntries();
        if (all.isEmpty()) {
            return null;
        }

        if (!properties.isAvoidRepeatsUntilExhausted()) {
            return all.get(ThreadLocalRandom.current().nextInt(all.size()));
        }

        if (shuffleBag.isEmpty()) {
            shuffleBag.addAll(all);
            Collections.shuffle(shuffleBag);
        }

        return shuffleBag.remove(shuffleBag.size() - 1);
    }
}