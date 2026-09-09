package com.qinghe.marketing.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StoreImportPreview {

    private final String importNo;
    private final StoreImportStatus status;
    private final String sourceVersion;
    private final int totalRows;
    private final int validRows;
    private final List<StoreImportRow> errors;

    public StoreImportPreview(String importNo, StoreImportStatus status, String sourceVersion,
                              int totalRows, int validRows, List<StoreImportRow> errors) {
        this.importNo = importNo;
        this.status = status;
        this.sourceVersion = sourceVersion;
        this.totalRows = totalRows;
        this.validRows = validRows;
        this.errors = Collections.unmodifiableList(new ArrayList<StoreImportRow>(errors));
    }

    public String importNo() { return importNo; }
    public StoreImportStatus status() { return status; }
    public String sourceVersion() { return sourceVersion; }
    public int totalRows() { return totalRows; }
    public int validRows() { return validRows; }
    public int errorRows() { return errors.size(); }
    public List<StoreImportRow> errors() { return errors; }
}
