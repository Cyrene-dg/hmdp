package com.qinghe.marketing.settlement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public final class SettlementBatchView {
    private final long id;
    private final String settlementBatchNo;
    private final String reconBatchNo;
    private final LocalDate businessDate;
    private final String status;
    private final int detailCount;
    private final int storeCount;
    private final long totalSubsidyFen;
    private final long version;
    private final String confirmedBy;
    private final LocalDateTime confirmedAt;
    private final List<SettlementDetailView> details;

    public SettlementBatchView(long id,String settlementBatchNo,String reconBatchNo,
            LocalDate businessDate,String status,int detailCount,int storeCount,
            long totalSubsidyFen,long version,String confirmedBy,LocalDateTime confirmedAt,
            List<SettlementDetailView> details) {
        this.id=id; this.settlementBatchNo=settlementBatchNo; this.reconBatchNo=reconBatchNo;
        this.businessDate=businessDate; this.status=status; this.detailCount=detailCount;
        this.storeCount=storeCount; this.totalSubsidyFen=totalSubsidyFen;
        this.version=version; this.confirmedBy=confirmedBy; this.confirmedAt=confirmedAt;
        this.details=details == null ? Collections.emptyList() : Collections.unmodifiableList(details);
    }
    public SettlementBatchView withDetails(List<SettlementDetailView> detailViews) {
        return new SettlementBatchView(id,settlementBatchNo,reconBatchNo,businessDate,status,
                detailCount,storeCount,totalSubsidyFen,version,confirmedBy,confirmedAt,detailViews);
    }
    public long id() { return id; }
    public String getSettlementBatchNo() { return settlementBatchNo; }
    public String getReconBatchNo() { return reconBatchNo; }
    public LocalDate getBusinessDate() { return businessDate; }
    public String getStatus() { return status; }
    public int getDetailCount() { return detailCount; }
    public int getStoreCount() { return storeCount; }
    public long getTotalSubsidyFen() { return totalSubsidyFen; }
    public long getVersion() { return version; }
    public String getConfirmedBy() { return confirmedBy; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public List<SettlementDetailView> getDetails() { return details; }
}
