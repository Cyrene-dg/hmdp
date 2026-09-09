package com.qinghe.marketing.identity;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class PlatformMemberIdGenerator {

    private final SecureRandom secureRandom;

    public PlatformMemberIdGenerator() {
        this(new SecureRandom());
    }

    PlatformMemberIdGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public long next() {
        long value;
        do {
            value = secureRandom.nextLong() & Long.MAX_VALUE;
        } while (value == 0L);
        return value;
    }
}
