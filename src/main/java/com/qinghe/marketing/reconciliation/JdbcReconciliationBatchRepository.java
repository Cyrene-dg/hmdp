package com.qinghe.marketing.reconciliation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcReconciliationBatchRepository implements ReconciliationBatchRepository {
    private static final String BATCH_SELECT = "SELECT id, recon_batch_no, provider, batch_no, "
            + "business_date, checksum, status, total_rows, imported_rows, success_rows, "
            + "error_rows, version FROM qh_recon_batch";
    private final JdbcTemplate jdbc;

    public JdbcReconciliationBatchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertBatchIfAbsent(String reconBatchNo, ReconciliationManifest manifest,
                                       LocalDateTime now) {
        LocalDateTime generatedAt = manifest.generatedAt().atZoneSameInstant(
                com.qinghe.marketing.shared.clock.BusinessClock.BUSINESS_ZONE).toLocalDateTime();
        return jdbc.update("INSERT IGNORE INTO qh_recon_batch (recon_batch_no, provider, batch_no, "
                        + "business_date, checksum, file_name, schema_version, checksum_algorithm, "
                        + "correction_of_batch_no, generated_at, status, total_rows, imported_rows, "
                        + "success_rows, error_rows, next_line_no, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, '1.0', 'SHA-256', ?, ?, 'RECEIVED', ?, 0, 0, 0, "
                        + "2, 0, ?, ?)", reconBatchNo, manifest.provider(), manifest.batchNo(),
                manifest.businessDate(), manifest.checksum(), manifest.fileName(),
                manifest.correctionOfBatchNo(), generatedAt, manifest.rowCount(), now, now) == 1;
    }

    @Override
    public Optional<ReconciliationBatch> findByProviderBatch(String provider,
                                                              String providerBatchNo) {
        return first(jdbc.query(BATCH_SELECT + " WHERE provider = ? AND batch_no = ?",
                new Object[]{provider, providerBatchNo}, batchMapper()));
    }

    @Override
    public Optional<ReconciliationBatch> findByProviderBatchForUpdate(String provider,
                                                                       String providerBatchNo) {
        return first(jdbc.query(BATCH_SELECT + " WHERE provider = ? AND batch_no = ? FOR UPDATE",
                new Object[]{provider, providerBatchNo}, batchMapper()));
    }

    @Override
    public Optional<ReconciliationBatch> findByIdForUpdate(long batchId) {
        return first(jdbc.query(BATCH_SELECT + " WHERE id = ? FOR UPDATE",
                new Object[]{batchId}, batchMapper()));
    }

    @Override
    public void recordAttempt(String provider, String providerBatchNo, String checksum,
                              String result, Long batchId, LocalDateTime now) {
        jdbc.update("INSERT INTO qh_recon_file_attempt (provider, provider_batch_no, checksum, "
                        + "result, recon_batch_id, received_at) VALUES (?, ?, ?, ?, ?, ?)",
                provider, providerBatchNo, checksum, result, batchId, now);
    }

    @Override
    public boolean correctionSourceExists(String provider, String providerBatchNo) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM qh_recon_batch "
                        + "WHERE provider = ? AND batch_no = ?",
                new Object[]{provider, providerBatchNo}, Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void createChunksIfAbsent(long batchId, int totalRows, int chunkSize,
                                     LocalDateTime now) {
        for (int offset = 0, chunkNo = 0; offset < totalRows; offset += chunkSize, chunkNo++) {
            int firstLine = offset + 2;
            int rows = Math.min(chunkSize, totalRows - offset);
            int lastLine = firstLine + rows - 1;
            jdbc.update("INSERT IGNORE INTO qh_recon_import_chunk (batch_id, chunk_no, "
                            + "first_line_no, last_line_no, row_count, status, attempt_count, "
                            + "success_rows, error_rows, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, 'PENDING', 0, 0, 0, ?, ?)",
                    batchId, chunkNo, firstLine, lastLine, rows, now, now);
        }
    }

    @Override
    public List<ReconciliationImportChunk> findIncompleteChunks(long batchId) {
        return jdbc.query("SELECT id, batch_id, chunk_no, first_line_no, last_line_no, row_count, "
                        + "status FROM qh_recon_import_chunk WHERE batch_id = ? "
                        + "AND status <> 'COMPLETED' ORDER BY chunk_no",
                new Object[]{batchId}, chunkMapper());
    }

    @Override
    public Optional<ReconciliationImportChunk> findChunkForUpdate(long batchId, int chunkNo) {
        return first(jdbc.query("SELECT id, batch_id, chunk_no, first_line_no, last_line_no, "
                        + "row_count, status FROM qh_recon_import_chunk "
                        + "WHERE batch_id = ? AND chunk_no = ? FOR UPDATE",
                new Object[]{batchId, chunkNo}, chunkMapper()));
    }

    @Override
    public void markImporting(long batchId, long expectedVersion, LocalDateTime now) {
        int changed = jdbc.update("UPDATE qh_recon_batch SET status = 'IMPORTING', "
                        + "last_error_code = NULL, version = version + 1, updated_at = ? "
                        + "WHERE id = ? AND version = ? AND status IN ('RECEIVED', 'IMPORTING')",
                now, batchId, expectedVersion);
        requireOne(changed, "reconciliation import state gate was lost");
    }

    @Override
    public void insertRecord(long batchId, int chunkNo, ReconciliationCsvRow row,
                             String rightCodeHash, LocalDateTime occurredAt, LocalDateTime now) {
        jdbc.update("INSERT INTO qh_recon_record (batch_id, line_no, store_code, terminal_no, "
                        + "pos_order_no, pos_request_no, redemption_no, right_code_hash, "
                        + "operation_type, operation_status, occurred_at, match_status, raw_digest, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "'UNMATCHED', ?, ?, ?) ON DUPLICATE KEY UPDATE id = id",
                batchId, row.lineNo(), row.storeCode(), row.terminalNo(), row.posOrderNo(),
                row.posRequestNo(), row.redemptionNo(), rightCodeHash, row.operationType(),
                row.operationStatus(), occurredAt, row.rawDigest(), now, now);
    }

    @Override
    public void insertIssue(long batchId, int chunkNo, ReconciliationRowIssue issue,
                            LocalDateTime now) {
        jdbc.update("INSERT INTO qh_recon_import_issue (batch_id, chunk_no, line_no, error_code, "
                        + "raw_digest, created_at) VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE id = id", batchId, chunkNo, issue.lineNo(),
                issue.code(), issue.rawDigest(), now);
    }

    @Override
    public void completeChunk(long chunkId, int successRows, int errorRows, LocalDateTime now) {
        int changed = jdbc.update("UPDATE qh_recon_import_chunk SET status = 'COMPLETED', "
                        + "attempt_count = attempt_count + 1, success_rows = ?, error_rows = ?, "
                        + "last_error_code = NULL, completed_at = ?, updated_at = ? "
                        + "WHERE id = ? AND status = 'PENDING'",
                successRows, errorRows, now, now, chunkId);
        requireOne(changed, "reconciliation chunk state gate was lost");
    }

    @Override
    public void recordChunkFailure(long batchId, int chunkNo, String errorCode,
                                   LocalDateTime now) {
        int changed = jdbc.update("UPDATE qh_recon_import_chunk SET attempt_count = attempt_count + 1, "
                        + "last_error_code = ?, updated_at = ? WHERE batch_id = ? AND chunk_no = ? "
                        + "AND status = 'PENDING'", errorCode, now, batchId, chunkNo);
        requireOne(changed, "reconciliation chunk failure cannot be recorded");
        jdbc.update("UPDATE qh_recon_batch SET last_error_code = ?, updated_at = ? "
                + "WHERE id = ? AND status = 'IMPORTING'", errorCode, now, batchId);
    }

    @Override
    public ReconciliationImportSummary summarizeImport(long batchId) {
        return jdbc.queryForObject("SELECT COUNT(*) AS total_chunks, "
                        + "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_chunks, "
                        + "COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN row_count ELSE 0 END), 0) "
                        + "AS imported_rows, COALESCE(SUM(success_rows), 0) AS success_rows, "
                        + "COALESCE(SUM(error_rows), 0) AS error_rows, "
                        + "COALESCE(MAX(CASE WHEN status = 'COMPLETED' THEN last_line_no + 1 END), 2) "
                        + "AS next_line_no FROM qh_recon_import_chunk WHERE batch_id = ?",
                new Object[]{batchId}, (rs, rowNum) -> new ReconciliationImportSummary(
                        rs.getInt("total_chunks"), rs.getInt("completed_chunks"),
                        rs.getInt("imported_rows"), rs.getInt("success_rows"),
                        rs.getInt("error_rows"), rs.getInt("next_line_no")));
    }

    @Override
    public void completeImport(long batchId, ReconciliationBatchStatus status,
                               ReconciliationImportSummary summary, long expectedVersion,
                               LocalDateTime now) {
        int changed = jdbc.update("UPDATE qh_recon_batch SET status = ?, imported_rows = ?, "
                        + "success_rows = ?, error_rows = ?, next_line_no = ?, "
                        + "last_error_code = ?, completed_at = ?, version = version + 1, "
                        + "updated_at = ? WHERE id = ? AND version = ? AND status = 'IMPORTING'",
                status.name(), summary.importedRows(), summary.successRows(), summary.errorRows(),
                summary.nextLineNo(), status == ReconciliationBatchStatus.PARTIAL_FAILED
                        ? "ROW_FORMAT_INVALID" : null,
                now, now, batchId, expectedVersion);
        requireOne(changed, "reconciliation import completion state gate was lost");
    }

    private RowMapper<ReconciliationBatch> batchMapper() {
        return (rs, rowNum) -> new ReconciliationBatch(rs.getLong("id"),
                rs.getString("recon_batch_no"), rs.getString("provider"),
                rs.getString("batch_no"), rs.getDate("business_date").toLocalDate(),
                rs.getString("checksum"), ReconciliationBatchStatus.valueOf(rs.getString("status")),
                rs.getInt("total_rows"), rs.getInt("imported_rows"),
                rs.getInt("success_rows"), rs.getInt("error_rows"), rs.getLong("version"));
    }

    private RowMapper<ReconciliationImportChunk> chunkMapper() {
        return (rs, rowNum) -> new ReconciliationImportChunk(rs.getLong("id"),
                rs.getLong("batch_id"), rs.getInt("chunk_no"), rs.getInt("first_line_no"),
                rs.getInt("last_line_no"), rs.getInt("row_count"),
                ReconciliationChunkStatus.valueOf(rs.getString("status")));
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.stream().findFirst();
    }

    private static void requireOne(int changed, String message) {
        if (changed != 1) throw new IllegalStateException(message);
    }
}
