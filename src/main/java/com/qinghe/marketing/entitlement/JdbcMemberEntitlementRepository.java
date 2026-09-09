package com.qinghe.marketing.entitlement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.campaign.BenefitType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public class JdbcMemberEntitlementRepository implements MemberEntitlementRepository {

    private static final String BASE_COLUMNS = "e.id, e.entitlement_no, e.right_code_hash, "
            + "e.encrypted_right_code, e.source_claim_id, e.campaign_id, e.member_id, e.status, "
            + "e.valid_from, e.valid_until, e.version, e.created_at, e.updated_at";
    private static final String VIEW_COLUMNS = BASE_COLUMNS + ", CAST(p.template_snapshot AS CHAR) "
            + "AS template_snapshot, (SELECT COUNT(*) FROM qh_campaign_store cs "
            + "WHERE cs.campaign_id = e.campaign_id AND cs.participation_status = 'ACTIVE') AS store_count";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMemberEntitlementRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public MemberEntitlement insert(MemberEntitlement entitlement) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO qh_member_entitlement (entitlement_no, right_code_hash, encrypted_right_code, "
                            + "source_claim_id, campaign_id, member_id, status, valid_from, valid_until, "
                            + "version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, entitlement.entitlementNo());
            statement.setString(2, entitlement.rightCodeHash());
            statement.setBytes(3, entitlement.encryptedRightCode());
            statement.setLong(4, entitlement.sourceClaimId());
            statement.setLong(5, entitlement.campaignId());
            statement.setLong(6, entitlement.memberId());
            statement.setString(7, entitlement.status().name());
            statement.setObject(8, entitlement.validFrom());
            statement.setObject(9, entitlement.validUntil());
            statement.setObject(10, entitlement.createdAt());
            statement.setObject(11, entitlement.updatedAt());
            return statement;
        }, keys);
        return findBySourceClaimId(entitlement.sourceClaimId()).orElseThrow(
                () -> new IllegalStateException("inserted entitlement cannot be reloaded"));
    }

    @Override
    public Optional<MemberEntitlement> findBySourceClaimId(long sourceClaimId) {
        List<MemberEntitlement> rows = jdbcTemplate.query("SELECT " + BASE_COLUMNS
                        + " FROM qh_member_entitlement e WHERE e.source_claim_id = ?",
                new Object[]{sourceClaimId}, entitlementMapper());
        return rows.stream().findFirst();
    }

    @Override
    public Optional<EntitlementView> findViewByNoAndMemberId(String entitlementNo, long memberId) {
        List<EntitlementView> rows = jdbcTemplate.query(viewSelect()
                        + " WHERE e.entitlement_no = ? AND e.member_id = ?",
                new Object[]{entitlementNo, memberId}, viewMapper());
        return rows.stream().findFirst();
    }

    @Override
    public List<EntitlementView> listViewsByMemberId(long memberId, EntitlementStatus status,
                                                      int offset, int limit) {
        String statusClause = status == null ? "" : " AND e.status = ?";
        if (status == null) {
            return jdbcTemplate.query(viewSelect() + " WHERE e.member_id = ? ORDER BY e.id DESC LIMIT ? OFFSET ?",
                    new Object[]{memberId, limit, offset}, viewMapper());
        }
        return jdbcTemplate.query(viewSelect() + " WHERE e.member_id = ?" + statusClause
                        + " ORDER BY e.id DESC LIMIT ? OFFSET ?",
                new Object[]{memberId, status.name(), limit, offset}, viewMapper());
    }

    @Override
    public long countByMemberId(long memberId, EntitlementStatus status) {
        Long count = status == null
                ? jdbcTemplate.queryForObject("SELECT COUNT(*) FROM qh_member_entitlement WHERE member_id = ?",
                Long.class, memberId)
                : jdbcTemplate.queryForObject("SELECT COUNT(*) FROM qh_member_entitlement "
                        + "WHERE member_id = ? AND status = ?", Long.class, memberId, status.name());
        return count == null ? 0L : count;
    }

    @Override
    public int expireAvailableBefore(LocalDateTime now) {
        return jdbcTemplate.update("UPDATE qh_member_entitlement SET status = 'EXPIRED', "
                        + "version = version + 1, updated_at = ? "
                        + "WHERE status = 'AVAILABLE' AND valid_until <= ?", now, now);
    }

    private String viewSelect() {
        return "SELECT " + VIEW_COLUMNS + " FROM qh_member_entitlement e "
                + "JOIN qh_campaign_publication_snapshot p ON p.campaign_id = e.campaign_id";
    }

    private RowMapper<MemberEntitlement> entitlementMapper() {
        return (resultSet, rowNum) -> new MemberEntitlement(
                resultSet.getLong("id"), resultSet.getString("entitlement_no"),
                resultSet.getString("right_code_hash"), resultSet.getBytes("encrypted_right_code"),
                resultSet.getLong("source_claim_id"), resultSet.getLong("campaign_id"),
                resultSet.getLong("member_id"), EntitlementStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("valid_from").toLocalDateTime(),
                resultSet.getTimestamp("valid_until").toLocalDateTime(), resultSet.getLong("version"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("updated_at").toLocalDateTime());
    }

    private RowMapper<EntitlementView> viewMapper() {
        RowMapper<MemberEntitlement> entitlementMapper = entitlementMapper();
        return (resultSet, rowNum) -> {
            MemberEntitlement entitlement = entitlementMapper.mapRow(resultSet, rowNum);
            try {
                JsonNode snapshot = objectMapper.readTree(resultSet.getString("template_snapshot"));
                return new EntitlementView(entitlement, requiredText(snapshot, "title"),
                        BenefitType.valueOf(requiredText(snapshot, "benefitType")),
                        objectMapper.writeValueAsString(snapshot.path("usageRules")),
                        resultSet.getInt("store_count"));
            } catch (Exception invalidSnapshot) {
                throw new IllegalStateException("published benefit snapshot is invalid", invalidSnapshot);
            }
        };
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().trim().isEmpty()) {
            throw new IllegalStateException("published benefit snapshot misses " + field);
        }
        return value.asText();
    }
}
