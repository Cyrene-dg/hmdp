package com.qinghe.marketing.settlement;

import java.util.List;

public final class SettlementPage {
    private final int pageNo; private final int pageSize; private final long total;
    private final List<SettlementBatchView> items;
    public SettlementPage(int pageNo,int pageSize,long total,List<SettlementBatchView> items) {
        this.pageNo=pageNo; this.pageSize=pageSize; this.total=total; this.items=items;
    }
    public int getPageNo() { return pageNo; }
    public int getPageSize() { return pageSize; }
    public long getTotal() { return total; }
    public List<SettlementBatchView> getItems() { return items; }
}
