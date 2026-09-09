package com.qinghe.marketing.shared.money;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Immutable RMB amount stored as integer fen. */
public final class MoneyFen implements Comparable<MoneyFen>, Serializable {

    public static final MoneyFen ZERO = new MoneyFen(0L);
    private final long value;

    private MoneyFen(long value) {
        this.value = value;
    }

    public static MoneyFen of(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("money must not be negative");
        }
        return value == 0 ? ZERO : new MoneyFen(value);
    }

    public static MoneyFen fromYuan(BigDecimal yuan) {
        Objects.requireNonNull(yuan, "yuan");
        return of(yuan.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact());
    }

    public long value() {
        return value;
    }

    public MoneyFen plus(MoneyFen other) {
        return of(Math.addExact(value, Objects.requireNonNull(other, "other").value));
    }

    @Override
    public int compareTo(MoneyFen other) {
        return Long.compare(value, other.value);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof MoneyFen && value == ((MoneyFen) other).value);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
