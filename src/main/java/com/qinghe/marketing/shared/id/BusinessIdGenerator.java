package com.qinghe.marketing.shared.id;

import com.qinghe.marketing.shared.clock.BusinessClock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class BusinessIdGenerator {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final BusinessClock clock;
    private final Supplier<UUID> uuidSupplier;

    @Autowired
    public BusinessIdGenerator(BusinessClock clock) {
        this(clock, UUID::randomUUID);
    }

    BusinessIdGenerator(BusinessClock clock, Supplier<UUID> uuidSupplier) {
        this.clock = clock;
        this.uuidSupplier = uuidSupplier;
    }

    public String next(BusinessIdType type) {
        String random = uuidSupplier.get().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
        return type.prefix() + "-" + TIME.format(clock.dateTime()) + "-" + random;
    }
}
