package com.qinghe.marketing.reconciliation;

public final class ReconciliationImportChunk {
    private final long id;
    private final long batchId;
    private final int chunkNo;
    private final int firstLineNo;
    private final int lastLineNo;
    private final int rowCount;
    private final ReconciliationChunkStatus status;

    public ReconciliationImportChunk(long id, long batchId, int chunkNo, int firstLineNo,
                                     int lastLineNo, int rowCount,
                                     ReconciliationChunkStatus status) {
        this.id = id;
        this.batchId = batchId;
        this.chunkNo = chunkNo;
        this.firstLineNo = firstLineNo;
        this.lastLineNo = lastLineNo;
        this.rowCount = rowCount;
        this.status = status;
    }

    public long id() { return id; }
    public long batchId() { return batchId; }
    public int chunkNo() { return chunkNo; }
    public int firstLineNo() { return firstLineNo; }
    public int lastLineNo() { return lastLineNo; }
    public int rowCount() { return rowCount; }
    public ReconciliationChunkStatus status() { return status; }
}
