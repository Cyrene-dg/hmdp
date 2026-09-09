package com.qinghe.marketing.shared.clock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Single time source for business rules. Business dates use China Standard Time. */
public interface BusinessClock {

    ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    Instant instant();

    default LocalDateTime dateTime() {
        return LocalDateTime.ofInstant(instant(), BUSINESS_ZONE);
    }

    default LocalDate businessDate() {
        return dateTime().toLocalDate();
    }
}
