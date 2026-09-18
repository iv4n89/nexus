package com.ivan.nexus.infrastructure.github;

import com.ivan.nexus.application.github.GitHubOAuthStateStore;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryGitHubOAuthStateStore implements GitHubOAuthStateStore {
    private static final Duration TTL = Duration.ofMinutes(10);

    private final Map<String, Instant> states = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryGitHubOAuthStateStore() {
        this(Clock.systemUTC());
    }

    InMemoryGitHubOAuthStateStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String issue() {
        purgeExpired();
        String state = UUID.randomUUID().toString();
        states.put(state, clock.instant().plus(TTL));
        return state;
    }

    @Override
    public boolean consume(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        purgeExpired();
        Instant expiry = states.remove(state);
        return expiry != null && !clock.instant().isAfter(expiry);
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        Iterator<Map.Entry<String, Instant>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Instant> entry = iterator.next();
            if (now.isAfter(entry.getValue())) {
                iterator.remove();
            }
        }
    }
}
