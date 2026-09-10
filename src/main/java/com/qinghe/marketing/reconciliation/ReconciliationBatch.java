package com.qinghe.marketing.reconciliation;

import java.time.LocalDate;

public final class ReconciliationBatch {
    private final long id;
    private final String reconBatchNo;
    private final String provider;
    private final String providerBatchNo;
    private final LocalDate businessDate;
    private final String checksum;
    private final ReconciliationBatchStatus status;
    private final int totalRows;
    private final int importedRows;
    private final int successRows;
    private final int errorRows;
    private final long version;

    public ReconciliationBatch(long id, String reconBatchNo, String provider,
                               String providerBatchNo, LocalDate businessDate, String checksum,
                               ReconciliationBatchStatus status, int totalRows, int importedRows,
                               int successRows, int errorRows, long version) {
        this.id = id;
        this.reconBatchNo = reconBatchNo;
        this.provider = provider;
        this.providerBatchNo = providerBatchNo;
        this.businessDate = businessDate;
        this.checksum = checksum;
        this.status = status;
        this.totalRows = totalRows;
        this.importedRows = importedRows;
        this.successRows = successRows;
        this.errorRows = errorRows;
        this.version = version;
    }

    public long id() { return id; }
    public String reconBatchNo() { return reconBatchNo; }
    public String provider() { return provider; }
    public String providerBatchNo() { return providerBatchNo; }
    public LocalDate businessDate() { return businessDate; }
    public String checksum() { return checksum; }
    public ReconciliationBatchStatus status() { return status; }
    public int totalRows() { return totalRows; }
    public int importedRows() { return importedRows; }
    public int successRows() { return successRows; }
    public int errorRows() { return errorRows; }
    public long version() { return version; }
}
