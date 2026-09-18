package com.ivan.nexus.application.traffic;

import com.ivan.nexus.domain.shared.DomainException;
import com.ivan.nexus.domain.shared.NexusErrorCode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

final class TrafficHours {
    record Window(Instant from, Instant to, Duration binSize) {}

    static Duration binSizeFor(int hours) {
        if (hours < 1 || hours > 168) {
            throw new DomainException(NexusErrorCode.OPERATION_NOT_ALLOWED,
                    "hours must be between 1 and 168");
        }
        return hours <= 24 ? Duration.ofMinutes(1) : Duration.ofMinutes(15);
    }

    static Window window(Clock clock, int hours) {
        Duration binSize = binSizeFor(hours);
        Instant to = clock.instant();
        Instant from = to.minus(hours, ChronoUnit.HOURS);
        return new Window(from, to, binSize);
    }
}
