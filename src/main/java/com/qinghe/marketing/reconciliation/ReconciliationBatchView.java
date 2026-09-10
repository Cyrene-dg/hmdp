package com.qinghe.marketing.reconciliation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public final class ReconciliationBatchView {
    private final long id;
    private final String reconBatchNo;
    private final String provider;
    private final String providerBatchNo;
    private final LocalDate businessDate;
    private final String checksum;
    private final String fileName;
    private final String status;
    private final int totalRows;
    private final int successRows;
    private final int errorRows;
    private final int matchedRows;
    private final int differenceRows;
    private final int directMatchedRows;
    private final int franchiseEligibleRows;
    private final String lastErrorCode;
    private final long version;
    private final LocalDateTime completedAt;
    private final List<ReconciliationIssueView> issues;
    private final List<ReconciliationDifferenceView> differences;

    public ReconciliationBatchView(long id, String reconBatchNo, String provider,
            String providerBatchNo, LocalDate businessDate, String checksum, String fileName,
            String status, int totalRows, int successRows, int errorRows, int matchedRows,
            int differenceRows, int directMatchedRows, int franchiseEligibleRows,
            String lastErrorCode, long version, LocalDateTime completedAt,
            List<ReconciliationIssueView> issues,
            List<ReconciliationDifferenceView> differences) {
        this.id=id; this.reconBatchNo=reconBatchNo; this.provider=provider;
        this.providerBatchNo=providerBatchNo; this.businessDate=businessDate;
        this.checksum=checksum; this.fileName=fileName; this.status=status;
        this.totalRows=totalRows; this.successRows=successRows; this.errorRows=errorRows;
        this.matchedRows=matchedRows; this.differenceRows=differenceRows;
        this.directMatchedRows=directMatchedRows;
        this.franchiseEligibleRows=franchiseEligibleRows; this.lastErrorCode=lastErrorCode;
        this.version=version; this.completedAt=completedAt;
        this.issues=issues == null ? Collections.emptyList() : Collections.unmodifiableList(issues);
        this.differences=differences == null ? Collections.emptyList()
                : Collections.unmodifiableList(differences);
    }

    public ReconciliationBatchView withDetails(List<ReconciliationIssueView> issueViews,
                                               List<ReconciliationDifferenceView> differenceViews) {
        return new ReconciliationBatchView(id,reconBatchNo,provider,providerBatchNo,businessDate,
                checksum,fileName,status,totalRows,successRows,errorRows,matchedRows,differenceRows,
                directMatchedRows,franchiseEligibleRows,lastErrorCode,version,completedAt,
                issueViews,differenceViews);
    }
    public long id() { return id; }
    public String fileName() { return fileName; }
    public String status() { return status; }
    public long version() { return version; }
    public String getReconBatchNo() { return reconBatchNo; }
    public String getProvider() { return provider; }
    public String getProviderBatchNo() { return providerBatchNo; }
    public LocalDate getBusinessDate() { return businessDate; }
    public String getChecksum() { return checksum; }
    public String getFileName() { return fileName; }
    public String getStatus() { return status; }
    public int getTotalRows() { return totalRows; }
    public int getSuccessRows() { return successRows; }
    public int getErrorRows() { return errorRows; }
    public int getMatchedRows() { return matchedRows; }
    public int getDifferenceRows() { return differenceRows; }
    public int getDirectMatchedRows() { return directMatchedRows; }
    public int getFranchiseEligibleRows() { return franchiseEligibleRows; }
    public String getLastErrorCode() { return lastErrorCode; }
    public long getVersion() { return version; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public List<ReconciliationIssueView> getIssues() { return issues; }
    public List<ReconciliationDifferenceView> getDifferences() { return differences; }
}
