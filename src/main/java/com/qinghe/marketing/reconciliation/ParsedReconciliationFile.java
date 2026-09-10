package com.qinghe.marketing.reconciliation;

import java.util.Collections;
import java.util.List;

public final class ParsedReconciliationFile {
    private final ReconciliationManifest manifest;
    private final List<ReconciliationCsvRow> rows;
    private final List<ReconciliationRowIssue> issues;

    public ParsedReconciliationFile(ReconciliationManifest manifest,
                                    List<ReconciliationCsvRow> rows,
                                    List<ReconciliationRowIssue> issues) {
        this.manifest = manifest;
        this.rows = Collections.unmodifiableList(rows);
        this.issues = Collections.unmodifiableList(issues);
    }
    public ReconciliationManifest manifest() { return manifest; }
    public List<ReconciliationCsvRow> rows() { return rows; }
    public List<ReconciliationRowIssue> issues() { return issues; }
}
