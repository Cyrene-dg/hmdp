package com.qinghe.marketing.reconciliation;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public final class ReconciliationManifest {
    private final String provider;
    private final String batchNo;
    private final LocalDate businessDate;
    private final String fileName;
    private final int rowCount;
    private final String checksum;
    private final OffsetDateTime generatedAt;
    private final String correctionOfBatchNo;

    public ReconciliationManifest(String provider, String batchNo, LocalDate businessDate,
                                  String fileName, int rowCount, String checksum,
                                  OffsetDateTime generatedAt, String correctionOfBatchNo) {
        this.provider = provider; this.batchNo = batchNo; this.businessDate = businessDate;
        this.fileName = fileName; this.rowCount = rowCount; this.checksum = checksum;
        this.generatedAt = generatedAt; this.correctionOfBatchNo = correctionOfBatchNo;
    }
    public String provider() { return provider; }
    public String batchNo() { return batchNo; }
    public LocalDate businessDate() { return businessDate; }
    public String fileName() { return fileName; }
    public int rowCount() { return rowCount; }
    public String checksum() { return checksum; }
    public OffsetDateTime generatedAt() { return generatedAt; }
    public String correctionOfBatchNo() { return correctionOfBatchNo; }
}
