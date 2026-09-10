package com.qinghe.marketing.reconciliation;

public final class ReconciliationDifferenceView {
    private final String differenceType;
    private final String businessKey;
    private final String detail;

    public ReconciliationDifferenceView(String differenceType, String businessKey, String detail) {
        this.differenceType = differenceType; this.businessKey = businessKey; this.detail = detail;
    }
    public String getDifferenceType() { return differenceType; }
    public String getBusinessKey() { return businessKey; }
    public String getDetail() { return detail; }
}
