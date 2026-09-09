package com.qinghe.marketing.shared.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyFenTest {

    @Test
    void shouldStoreExactIntegerFen() {
        assertEquals(1234L, MoneyFen.fromYuan(new BigDecimal("12.34")).value());
        assertEquals(1500L, MoneyFen.of(500).plus(MoneyFen.of(1000)).value());
    }

    @Test
    void shouldRejectNegativeAndFractionalFen() {
        assertThrows(IllegalArgumentException.class, () -> MoneyFen.of(-1));
        assertThrows(ArithmeticException.class,
                () -> MoneyFen.fromYuan(new BigDecimal("0.001")));
    }

    @Test
    void shouldFailOnOverflow() {
        assertThrows(ArithmeticException.class,
                () -> MoneyFen.of(Long.MAX_VALUE).plus(MoneyFen.of(1)));
    }
}
