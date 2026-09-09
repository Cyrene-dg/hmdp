package com.qinghe.marketing.store;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcStoreImportRepository implements StoreImportRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcStoreImportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<StoreImportBatch> findBySourceVersion(String sourceVersion) {
        return findOne("source_version", sourceVersion);
    }

    @Override
    public Optional<StoreImportBatch> findByImportNo(String importNo) {
        return findOne("import_no", importNo);
    }

    @Override
    public StoreImportBatch save(StoreImportBatch batch) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO qh_store_import_batch "
                            + "(import_no, source_version, file_sha256, status, total_rows, valid_rows, "
                            + "error_rows, created_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, batch.importNo());
            statement.setString(2, batch.sourceVersion());
            statement.setString(3, batch.fileSha256());
            statement.setString(4, batch.status().name());
            statement.setInt(5, batch.totalRows());
            statement.setInt(6, batch.validRows());
            statement.setInt(7, batch.errorRows());
            statement.setString(8, batch.createdBy());
            statement.setObject(9, batch.createdAt());
            return statement;
        }, keyHolder);
        Number generated = keyHolder.getKey();
        if (generated == null) {
            throw new IllegalStateException("store import insert did not return an id");
        }
        long batchId = generated.longValue();
        saveRows(batchId, batch.rows(), batch.createdAt());
        return new StoreImportBatch(batchId, batch.importNo(), batch.sourceVersion(),
                batch.fileSha256(), batch.status(), batch.createdBy(), batch.createdAt(),
                null, batch.rows());
    }

    @Override
    public void markCommitted(long batchId, LocalDateTime committedAt) {
        int updated = jdbcTemplate.update("UPDATE qh_store_import_batch SET status = 'COMMITTED', "
                        + "committed_at = ? WHERE id = ? AND status = 'PREVIEWED'",
                committedAt, batchId);
        if (updated != 1) {
            throw new IllegalStateException("store import batch was not in PREVIEWED state");
        }
    }

    private Optional<StoreImportBatch> findOne(String column, String value) {
        String sql = "SELECT id, import_no, source_version, file_sha256, status, created_by, "
                + "created_at, committed_at FROM qh_store_import_batch WHERE " + column + " = ?";
        List<StoreImportBatch> batches = jdbcTemplate.query(sql, new Object[]{value}, (resultSet, rowNum) -> {
            long id = resultSet.getLong("id");
            String sourceVersion = resultSet.getString("source_version");
            Timestamp committed = resultSet.getTimestamp("committed_at");
            return new StoreImportBatch(id,
                    resultSet.getString("import_no"),
                    sourceVersion,
                    resultSet.getString("file_sha256"),
                    StoreImportStatus.valueOf(resultSet.getString("status")),
                    resultSet.getString("created_by"),
                    resultSet.getTimestamp("created_at").toLocalDateTime(),
                    committed == null ? null : committed.toLocalDateTime(),
                    findRows(id, sourceVersion));
        });
        return batches.stream().findFirst();
    }

    private List<StoreImportRow> findRows(long batchId, String sourceVersion) {
        return jdbcTemplate.query("SELECT source_row_number, external_store_code, store_name, ownership_type, "
                        + "store_status, pos_version, validation_error FROM qh_store_import_row "
                        + "WHERE batch_id = ? ORDER BY source_row_number",
                new Object[]{batchId},
                (resultSet, rowNum) -> new StoreImportRow(
                        resultSet.getInt("source_row_number"),
                        sourceVersion,
                        resultSet.getString("external_store_code"),
                        resultSet.getString("store_name"),
                        enumOrNull(StoreOwnershipType.class, resultSet.getString("ownership_type")),
                        enumOrNull(StoreStatus.class, resultSet.getString("store_status")),
                        resultSet.getString("pos_version"),
                        resultSet.getString("validation_error")));
    }

    private void saveRows(long batchId, List<StoreImportRow> rows, LocalDateTime createdAt) {
        if (rows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("INSERT INTO qh_store_import_row "
                        + "(batch_id, source_row_number, external_store_code, store_name, ownership_type, "
                        + "store_status, pos_version, validation_error, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement statement, int index) throws SQLException {
                        StoreImportRow row = rows.get(index);
                        statement.setLong(1, batchId);
                        statement.setInt(2, row.rowNumber());
                        statement.setString(3, row.externalStoreCode());
                        statement.setString(4, row.storeName());
                        statement.setString(5, row.ownershipType() == null ? null : row.ownershipType().name());
                        statement.setString(6, row.storeStatus() == null ? null : row.storeStatus().name());
                        statement.setString(7, row.posVersion());
                        statement.setString(8, row.validationError());
                        statement.setObject(9, createdAt);
                    }

                    @Override
                    public int getBatchSize() {
                        return rows.size();
                    }
                });
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }
}
