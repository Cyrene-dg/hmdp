package com.qinghe.marketing.reversal;

import com.qinghe.marketing.entitlement.EntitlementStatus;
import java.time.LocalDateTime;

public final class ReversalEntitlement {
    private final long id;
    private final EntitlementStatus status;
    private final LocalDateTime validUntil;
    private final long version;

    public ReversalEntitlement(long id, EntitlementStatus status,
                               LocalDateTime validUntil, long version) {
        this.id = id; this.status = status; this.validUntil = validUntil; this.version = version;
    }
    public long id() { return id; }
    public EntitlementStatus status() { return status; }
    public LocalDateTime validUntil() { return validUntil; }
    public long version() { return version; }
}
