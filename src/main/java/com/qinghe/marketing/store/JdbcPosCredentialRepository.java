package com.qinghe.marketing.store;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcPosCredentialRepository implements PosCredentialRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPosCredentialRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PosCredential> findActiveByClientId(String clientId) {
        List<PosCredential> credentials = jdbcTemplate.query(
                "SELECT store_id, client_id, secret_reference, secret_version "
                        + "FROM qh_pos_credential WHERE client_id = ? AND status = 'ACTIVE'",
                new Object[]{clientId},
                (resultSet, rowNum) -> new PosCredential(
                        resultSet.getLong("store_id"), resultSet.getString("client_id"),
                        resultSet.getString("secret_reference"), resultSet.getInt("secret_version")));
        return credentials.stream().findFirst();
    }

    @Override
    public void activate(PosCredential credential, LocalDateTime now) {
        jdbcTemplate.update("UPDATE qh_pos_credential SET status = 'REVOKED', revoked_at = ?, "
                        + "version = version + 1, updated_at = ? "
                        + "WHERE store_id = ? AND client_id <> ? AND status = 'ACTIVE'",
                now, now, credential.storeId(), credential.clientId());
        if (activateExisting(credential, now) == 1) {
            return;
        }
        try {
            jdbcTemplate.update("INSERT INTO qh_pos_credential "
                            + "(store_id, client_id, secret_reference, secret_version, status, activated_at, "
                            + "version, created_at, updated_at) VALUES (?, ?, ?, ?, 'ACTIVE', ?, 0, ?, ?)",
                    credential.storeId(), credential.clientId(), credential.secretReference(),
                    credential.secretVersion(), now, now, now);
        } catch (DuplicateKeyException concurrentActivation) {
            if (activateExisting(credential, now) != 1) {
                throw new QingheBusinessException(QingheErrorCode.BUSINESS_STATE_CONFLICT,
                        "POS client id is already bound to a different store");
            }
        }
    }

    private int activateExisting(PosCredential credential, LocalDateTime now) {
        return jdbcTemplate.update("UPDATE qh_pos_credential SET secret_reference = ?, secret_version = ?, "
                        + "status = 'ACTIVE', activated_at = ?, revoked_at = NULL, version = version + 1, "
                        + "updated_at = ? WHERE client_id = ? AND store_id = ?",
                credential.secretReference(), credential.secretVersion(), now, now,
                credential.clientId(), credential.storeId());
    }
}
