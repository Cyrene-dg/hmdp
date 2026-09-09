package com.qinghe.marketing.identity;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class JdbcPosNonceRepository implements PosNonceRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPosNonceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean reserve(String clientId, String nonce, LocalDateTime expiresAt, LocalDateTime now) {
        jdbcTemplate.update("DELETE FROM qh_pos_nonce WHERE client_id = ? AND nonce_value = ? AND expires_at < ?",
                clientId, nonce, now);
        try {
            jdbcTemplate.update("INSERT INTO qh_pos_nonce "
                            + "(client_id, nonce_value, expires_at, created_at) VALUES (?, ?, ?, ?)",
                    clientId, nonce, expiresAt, now);
            return true;
        } catch (DuplicateKeyException replay) {
            return false;
        }
    }
}
