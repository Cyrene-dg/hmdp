package com.qinghe.marketing.shared.clock;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class SystemBusinessClock implements BusinessClock {

    private final Clock clock;

    public SystemBusinessClock() {
        this(Clock.systemUTC());
    }

    SystemBusinessClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant instant() {
        return clock.instant();
    }
}
