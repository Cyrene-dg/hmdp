package com.qinghe.marketing.entitlement;

import java.util.Collections;
import java.util.List;

public final class EntitlementPage {

    private final List<EntitlementView> items;
    private final int pageNo;
    private final int pageSize;
    private final long total;

    public EntitlementPage(List<EntitlementView> items, int pageNo, int pageSize, long total) {
        this.items = Collections.unmodifiableList(items);
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.total = total;
    }

    public List<EntitlementView> items() { return items; }
    public int pageNo() { return pageNo; }
    public int pageSize() { return pageSize; }
    public long total() { return total; }
}

