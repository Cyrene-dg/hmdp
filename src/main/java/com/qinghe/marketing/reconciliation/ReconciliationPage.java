package com.qinghe.marketing.reconciliation;

import java.util.List;

public final class ReconciliationPage {
    private final int pageNo;
    private final int pageSize;
    private final long total;
    private final List<ReconciliationBatchView> items;

    public ReconciliationPage(int pageNo, int pageSize, long total,
                              List<ReconciliationBatchView> items) {
        this.pageNo=pageNo; this.pageSize=pageSize; this.total=total; this.items=items;
    }
    public int getPageNo() { return pageNo; }
    public int getPageSize() { return pageSize; }
    public long getTotal() { return total; }
    public List<ReconciliationBatchView> getItems() { return items; }
}
