package com.qinghe.marketing.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcStoreRepository implements StoreRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcStoreRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<StoreRecord> findById(long id) {
        return findOne("id", id);
    }

    @Override
    public Optional<StoreRecord> findByExternalStoreCode(String externalStoreCode) {
        return findOne("external_store_code", externalStoreCode);
    }

    private Optional<StoreRecord> findOne(String column, Object value) {
        List<StoreRecord> stores = jdbcTemplate.query(
                "SELECT id, external_store_code, name, ownership_type, status, pos_version, "
                        + "source_version, version FROM qh_store WHERE " + column + " = ?",
                new Object[]{value},
                (resultSet, rowNum) -> new StoreRecord(
                        resultSet.getLong("id"),
                        resultSet.getString("external_store_code"),
                        resultSet.getString("name"),
                        StoreOwnershipType.valueOf(resultSet.getString("ownership_type")),
                        StoreStatus.valueOf(resultSet.getString("status")),
                        resultSet.getString("pos_version"),
                        resultSet.getString("source_version"),
                        resultSet.getLong("version")));
        return stores.stream().findFirst();
    }

    @Override
    public void upsert(StoreImportRow row, LocalDateTime now) {
        if (updateExisting(row, now) == 1) {
            return;
        }
        try {
            jdbcTemplate.update("INSERT INTO qh_store "
                            + "(external_store_code, name, ownership_type, status, pos_version, source_version, "
                            + "version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 0, ?, ?)",
                    row.externalStoreCode(), row.storeName(), row.ownershipType().name(), row.storeStatus().name(),
                    row.posVersion(), row.sourceVersion(), now, now);
        } catch (DuplicateKeyException concurrentInsert) {
            if (updateExisting(row, now) != 1) {
                throw concurrentInsert;
            }
        }
    }

    private int updateExisting(StoreImportRow row, LocalDateTime now) {
        return jdbcTemplate.update("UPDATE qh_store SET name = ?, ownership_type = ?, status = ?, "
                        + "pos_version = ?, source_version = ?, version = version + 1, updated_at = ? "
                        + "WHERE external_store_code = ?",
                row.storeName(), row.ownershipType().name(), row.storeStatus().name(), row.posVersion(),
                row.sourceVersion(), now, row.externalStoreCode());
    }
}
