package com.qinghe.marketing.operations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/** Bounded diagnostic snapshot of timed-out Redis claim reservations. */
public final class OrphanReservationInspection {
    private final long campaignId;
    private final LocalDateTime cutoff;
    private final int scanned;
    private final boolean truncated;
    private final List<OperationalExceptionView> items;

    public OrphanReservationInspection(long campaignId, LocalDateTime cutoff, int scanned,
                                        boolean truncated,
                                        List<OperationalExceptionView> items) {
        this.campaignId = campaignId;
        this.cutoff = cutoff;
        this.scanned = scanned;
        this.truncated = truncated;
        this.items = Collections.unmodifiableList(items);
    }

    public long getCampaignId() { return campaignId; }
    public LocalDateTime getCutoff() { return cutoff; }
    public int getScanned() { return scanned; }
    public boolean isTruncated() { return truncated; }
    public List<OperationalExceptionView> getItems() { return items; }
}
