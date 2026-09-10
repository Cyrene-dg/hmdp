package com.qinghe.marketing.settlement;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class SettlementBatch {
    private final long id;
    private final String batchNo;
    private final long reconBatchId;
    private final String reconBatchNo;
    private final LocalDate businessDate;
    private final SettlementBatchStatus status;
    private final int detailCount;
    private final int storeCount;
    private final long totalFen;
    private final long version;
    private final String confirmedBy;
    private final LocalDateTime confirmedAt;

    public SettlementBatch(long id, String batchNo, long reconBatchId, String reconBatchNo,
                           LocalDate businessDate, SettlementBatchStatus status, int detailCount,
                           int storeCount, long totalFen, long version, String confirmedBy,
                           LocalDateTime confirmedAt) {
        this.id = id; this.batchNo = batchNo; this.reconBatchId = reconBatchId;
        this.reconBatchNo = reconBatchNo; this.businessDate = businessDate;
        this.status = status; this.detailCount = detailCount; this.storeCount = storeCount;
        this.totalFen = totalFen; this.version = version;
        this.confirmedBy = confirmedBy; this.confirmedAt = confirmedAt;
    }

    public long id() { return id; }
    public String batchNo() { return batchNo; }
    public long reconBatchId() { return reconBatchId; }
    public String reconBatchNo() { return reconBatchNo; }
    public LocalDate businessDate() { return businessDate; }
    public SettlementBatchStatus status() { return status; }
    public int detailCount() { return detailCount; }
    public int storeCount() { return storeCount; }
    public long totalFen() { return totalFen; }
    public long version() { return version; }
    public String confirmedBy() { return confirmedBy; }
    public LocalDateTime confirmedAt() { return confirmedAt; }
}
