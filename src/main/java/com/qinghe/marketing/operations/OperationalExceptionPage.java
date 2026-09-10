package com.qinghe.marketing.operations;

import java.util.List;

public final class OperationalExceptionPage {
    private final int pageNo;
    private final int pageSize;
    private final long total;
    private final List<OperationalExceptionView> items;

    public OperationalExceptionPage(int pageNo, int pageSize, long total,
                                    List<OperationalExceptionView> items) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.total = total;
        this.items = items;
    }

    public int getPageNo() { return pageNo; }
    public int getPageSize() { return pageSize; }
    public long getTotal() { return total; }
    public List<OperationalExceptionView> getItems() { return items; }
}
