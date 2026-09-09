package com.qinghe.marketing.identity;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcMemberMappingRepository implements MemberMappingRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMemberMappingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<MemberMapping> findByExternalMemberNo(String externalMemberNo) {
        List<MemberMapping> rows = jdbcTemplate.query(
                "SELECT id, external_member_no, platform_user_id, status_snapshot "
                        + "FROM qh_member_mapping WHERE external_member_no = ?",
                new Object[]{externalMemberNo},
                (resultSet, rowNum) -> new MemberMapping(
                        resultSet.getLong("id"),
                        resultSet.getString("external_member_no"),
                        resultSet.getLong("platform_user_id"),
                        MemberStatus.valueOf(resultSet.getString("status_snapshot"))));
        return rows.stream().findFirst();
    }

    @Override
    public MemberMapping findOrCreate(String externalMemberNo, long candidatePlatformUserId,
                                      MemberStatus statusSnapshot, LocalDateTime now) {
        Optional<MemberMapping> existing = findByExternalMemberNo(externalMemberNo);
        if (existing.isPresent()) {
            return refreshStatus(existing.get(), statusSnapshot, now);
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO qh_member_mapping "
                                + "(external_member_no, platform_user_id, status_snapshot, version, created_at, updated_at) "
                                + "VALUES (?, ?, ?, 0, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, externalMemberNo);
                statement.setLong(2, candidatePlatformUserId);
                statement.setString(3, statusSnapshot.name());
                statement.setObject(4, now);
                statement.setObject(5, now);
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException("member mapping insert did not return an id");
            }
            return new MemberMapping(key.longValue(), externalMemberNo,
                    candidatePlatformUserId, statusSnapshot);
        } catch (DuplicateKeyException duplicate) {
            return findByExternalMemberNo(externalMemberNo)
                    .map(mapping -> refreshStatus(mapping, statusSnapshot, now))
                    .orElseThrow(() -> duplicate);
        }
    }

    private MemberMapping refreshStatus(MemberMapping mapping, MemberStatus status, LocalDateTime now) {
        if (mapping.statusSnapshot() != status) {
            jdbcTemplate.update("UPDATE qh_member_mapping SET status_snapshot = ?, "
                            + "version = version + 1, updated_at = ? WHERE id = ?",
                    status.name(), now, mapping.id());
            return new MemberMapping(mapping.id(), mapping.externalMemberNo(),
                    mapping.platformUserId(), status);
        }
        return mapping;
    }
}
