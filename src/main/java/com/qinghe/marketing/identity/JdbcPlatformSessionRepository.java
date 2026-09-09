package com.qinghe.marketing.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcPlatformSessionRepository implements PlatformSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPlatformSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(PlatformSessionRecord session) {
        jdbcTemplate.update("INSERT INTO qh_platform_session "
                        + "(session_no, access_token_hash, member_id, status, issued_at, expires_at, "
                        + "created_at, updated_at) VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, ?)",
                session.sessionNo(), session.accessTokenHash(), session.memberId(),
                session.issuedAt(), session.expiresAt(), session.issuedAt(), session.issuedAt());
    }

    @Override
    public Optional<AuthenticatedMember> findActiveMember(String accessTokenHash, LocalDateTime now) {
        List<AuthenticatedMember> rows = jdbcTemplate.query(
                "SELECT m.id AS member_id, m.platform_user_id, m.external_member_no, s.expires_at "
                        + "FROM qh_platform_session s JOIN qh_member_mapping m ON m.id = s.member_id "
                        + "WHERE s.access_token_hash = ? AND s.status = 'ACTIVE' "
                        + "AND s.expires_at > ? AND m.status_snapshot = 'ACTIVE'",
                new Object[]{accessTokenHash, now},
                (resultSet, rowNum) -> new AuthenticatedMember(
                        resultSet.getLong("member_id"),
                        resultSet.getLong("platform_user_id"),
                        resultSet.getString("external_member_no"),
                        resultSet.getTimestamp("expires_at").toLocalDateTime()));
        if (!rows.isEmpty()) {
            jdbcTemplate.update("UPDATE qh_platform_session SET last_access_at = ?, updated_at = ? "
                            + "WHERE access_token_hash = ?",
                    now, now, accessTokenHash);
        }
        return rows.stream().findFirst();
    }
}
