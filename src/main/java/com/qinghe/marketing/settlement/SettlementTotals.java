package com.qinghe.marketing.settlement;

import java.util.Collections;
import java.util.List;

public final class SettlementTotals {
    private final List<Long> detailIds;
    private final int storeCount;
    private final long totalFen;

    public SettlementTotals(List<Long> detailIds, int storeCount, long totalFen) {
        this.detailIds = Collections.unmodifiableList(detailIds);
        this.storeCount = storeCount;
        this.totalFen = totalFen;
    }

    public List<Long> detailIds() { return detailIds; }
    public int detailCount() { return detailIds.size(); }
    public int storeCount() { return storeCount; }
    public long totalFen() { return totalFen; }
}
