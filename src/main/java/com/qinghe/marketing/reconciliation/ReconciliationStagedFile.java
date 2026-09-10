package com.qinghe.marketing.reconciliation;

import java.nio.file.Path;

public final class ReconciliationStagedFile {
    private final Path directory;
    private final Path manifest;
    private final Path csv;

    public ReconciliationStagedFile(Path directory, Path manifest, Path csv) {
        this.directory = directory;
        this.manifest = manifest;
        this.csv = csv;
    }

    public Path directory() { return directory; }
    public Path manifest() { return manifest; }
    public Path csv() { return csv; }
}
