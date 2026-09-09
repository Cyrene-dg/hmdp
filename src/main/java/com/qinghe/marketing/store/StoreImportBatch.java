package com.qinghe.marketing.store;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StoreImportBatch {

    private final long id;
    private final String importNo;
    private final String sourceVersion;
    private final String fileSha256;
    private final StoreImportStatus status;
    private final String createdBy;
    private final LocalDateTime createdAt;
    private final LocalDateTime committedAt;
    private final List<StoreImportRow> rows;

    public StoreImportBatch(long id, String importNo, String sourceVersion, String fileSha256,
                            StoreImportStatus status, String createdBy, LocalDateTime createdAt,
                            LocalDateTime committedAt, List<StoreImportRow> rows) {
        this.id = id;
        this.importNo = importNo;
        this.sourceVersion = sourceVersion;
        this.fileSha256 = fileSha256;
        this.status = status;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.committedAt = committedAt;
        this.rows = Collections.unmodifiableList(new ArrayList<StoreImportRow>(rows));
    }

    public long id() { return id; }
    public String importNo() { return importNo; }
    public String sourceVersion() { return sourceVersion; }
    public String fileSha256() { return fileSha256; }
    public StoreImportStatus status() { return status; }
    public String createdBy() { return createdBy; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime committedAt() { return committedAt; }
    public List<StoreImportRow> rows() { return rows; }

    public int totalRows() { return rows.size(); }
    public int errorRows() { return (int) rows.stream().filter(row -> !row.valid()).count(); }
    public int validRows() { return totalRows() - errorRows(); }
}
