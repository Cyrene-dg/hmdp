package com.qinghe.marketing.redemption;

import com.qinghe.marketing.campaign.BenefitTemplateDraft;
import com.qinghe.marketing.campaign.BenefitTemplateSnapshotCodec;
import com.qinghe.marketing.entitlement.EntitlementStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcRedemptionRepository implements RedemptionRepository {

    private static final String REQUEST_SELECT = "SELECT r.id, r.pos_client_id, r.pos_request_no, "
            + "r.request_digest, r.status, d.redemption_no, r.failure_code, r.right_status, "
            + "r.original_redemption_no, r.first_processed_at, r.version "
            + "FROM qh_pos_redemption_request r LEFT JOIN qh_redemption d ON d.id = r.redemption_id "
            + "WHERE r.pos_client_id = ? AND r.pos_request_no = ?";
    private static final String ENTITLEMENT_SELECT = "SELECT e.id, e.entitlement_no, e.campaign_id, "
            + "e.status, e.valid_from, e.valid_until, e.version, CAST(p.template_snapshot AS CHAR) "
            + "AS template_snapshot FROM qh_member_entitlement e "
            + "JOIN qh_campaign_publication_snapshot p ON p.campaign_id = e.campaign_id "
            + "WHERE e.right_code_hash = ?";

    private final JdbcTemplate jdbcTemplate;
    private final BenefitTemplateSnapshotCodec snapshotCodec;

    public JdbcRedemptionRepository(JdbcTemplate jdbcTemplate,
                                    BenefitTemplateSnapshotCodec snapshotCodec) {
        this.jdbcTemplate = jdbcTemplate;
        this.snapshotCodec = snapshotCodec;
    }

    @Override
    public void createRequestIfAbsent(String posClientId, String posRequestNo,
                                      String requestDigest, long storeId, LocalDateTime now) {
        jdbcTemplate.update("INSERT IGNORE INTO qh_pos_redemption_request "
                        + "(pos_client_id, pos_request_no, request_digest, store_id, status, version, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, 'PROCESSING', 0, ?, ?)",
                posClientId, posRequestNo, requestDigest, storeId, now, now);
    }

    @Override
    public Optional<PosRedemptionRequest> findRequest(String posClientId, String posRequestNo) {
        return first(jdbcTemplate.query(REQUEST_SELECT,
                new Object[]{posClientId, posRequestNo}, requestMapper()));
    }

    @Override
    public Optional<PosRedemptionRequest> findRequestForUpdate(String posClientId,
                                                               String posRequestNo) {
        return first(jdbcTemplate.query(REQUEST_SELECT + " FOR UPDATE",
                new Object[]{posClientId, posRequestNo}, requestMapper()));
    }

    @Override
    public Optional<PosEntitlementSnapshot> findEntitlementByRightCodeHash(String rightCodeHash) {
        return first(jdbcTemplate.query(ENTITLEMENT_SELECT,
                new Object[]{rightCodeHash}, entitlementMapper()));
    }

    @Override
    public Optional<PosEntitlementSnapshot> findEntitlementByRightCodeHashForUpdate(
            String rightCodeHash) {
        return first(jdbcTemplate.query(ENTITLEMENT_SELECT + " FOR UPDATE",
                new Object[]{rightCodeHash}, entitlementMapper()));
    }

    @Override
    public Optional<String> findSuccessfulRedemptionNoByEntitlementId(long entitlementId) {
        List<String> rows = jdbcTemplate.query("SELECT redemption_no FROM qh_redemption "
                        + "WHERE entitlement_id = ? AND status = 'SUCCESS' ORDER BY id LIMIT 1",
                new Object[]{entitlementId}, (rs, rowNum) -> rs.getString(1));
        return first(rows);
    }

    @Override
    public boolean markEntitlementUsed(long entitlementId, long expectedVersion,
                                       LocalDateTime now) {
        return jdbcTemplate.update("UPDATE qh_member_entitlement SET status = 'USED', "
                        + "version = version + 1, updated_at = ? WHERE id = ? AND status = 'AVAILABLE' "
                        + "AND version = ? AND valid_from <= ? AND valid_until > ?",
                now, entitlementId, expectedVersion, now, now) == 1;
    }

    @Override
    public Redemption insertRedemption(Redemption redemption, LocalDateTime now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO qh_redemption (redemption_no, pos_client_id, pos_request_no, "
                            + "request_digest, pos_order_no, terminal_no, operator_no, entitlement_id, "
                            + "store_id, status, occurred_at, first_processed_at, version, created_at, "
                            + "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, redemption.redemptionNo());
            statement.setString(2, redemption.posClientId());
            statement.setString(3, redemption.posRequestNo());
            statement.setString(4, redemption.requestDigest());
            statement.setString(5, redemption.posOrderNo());
            statement.setString(6, redemption.terminalNo());
            statement.setString(7, redemption.operatorNo());
            statement.setLong(8, redemption.entitlementId());
            statement.setLong(9, redemption.storeId());
            statement.setString(10, redemption.status().name());
            statement.setObject(11, redemption.occurredAt());
            statement.setObject(12, redemption.firstProcessedAt());
            statement.setObject(13, now);
            statement.setObject(14, now);
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("redemption insert did not return an id");
        return new Redemption(id.longValue(), redemption.redemptionNo(), redemption.posClientId(),
                redemption.posRequestNo(), redemption.requestDigest(), redemption.posOrderNo(),
                redemption.terminalNo(), redemption.operatorNo(), redemption.entitlementId(),
                redemption.storeId(), redemption.status(), redemption.occurredAt(),
                redemption.firstProcessedAt());
    }

    @Override
    public void insertSubsidyCandidate(SubsidyCandidate candidate) {
        jdbcTemplate.update("INSERT INTO qh_subsidy_candidate (redemption_id, campaign_id, store_id, "
                        + "subsidy_fen, status, snapshot_version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", candidate.redemptionId(),
                candidate.campaignId(), candidate.storeId(), candidate.subsidyFen(),
                candidate.status().name(), candidate.snapshotVersion(), candidate.createdAt(),
                candidate.createdAt());
    }

    @Override
    public void completeRequestSuccess(long requestId, long expectedVersion, long redemptionId,
                                       LocalDateTime completedAt) {
        int changed = jdbcTemplate.update("UPDATE qh_pos_redemption_request SET status = 'SUCCESS', "
                        + "redemption_id = ?, right_status = 'USED', first_processed_at = ?, "
                        + "version = version + 1, updated_at = ? WHERE id = ? AND version = ? "
                        + "AND status = 'PROCESSING'", redemptionId, completedAt, completedAt,
                requestId, expectedVersion);
        requireChanged(changed, "POS request success transition lost its state gate");
    }

    @Override
    public void completeRequestFailure(long requestId, long expectedVersion, String failureCode,
                                       EntitlementStatus rightStatus, String originalRedemptionNo,
                                       LocalDateTime completedAt) {
        int changed = jdbcTemplate.update("UPDATE qh_pos_redemption_request SET status = 'FAILED', "
                        + "failure_code = ?, right_status = ?, original_redemption_no = ?, "
                        + "first_processed_at = ?, version = version + 1, updated_at = ? "
                        + "WHERE id = ? AND version = ? AND status = 'PROCESSING'",
                failureCode, rightStatus == null ? null : rightStatus.name(), originalRedemptionNo,
                completedAt, completedAt, requestId, expectedVersion);
        requireChanged(changed, "POS request failure transition lost its state gate");
    }

    private RowMapper<PosRedemptionRequest> requestMapper() {
        return (rs, rowNum) -> new PosRedemptionRequest(rs.getLong("id"),
                rs.getString("pos_client_id"), rs.getString("pos_request_no"),
                rs.getString("request_digest"), PosRequestStatus.valueOf(rs.getString("status")),
                rs.getString("redemption_no"), rs.getString("failure_code"),
                nullableStatus(rs.getString("right_status")), rs.getString("original_redemption_no"),
                rs.getObject("first_processed_at", LocalDateTime.class),
                rs.getLong("version"));
    }

    private RowMapper<PosEntitlementSnapshot> entitlementMapper() {
        return (rs, rowNum) -> {
            BenefitTemplateDraft template = snapshotCodec.decode(rs.getString("template_snapshot"));
            return new PosEntitlementSnapshot(rs.getLong("id"), rs.getString("entitlement_no"),
                    rs.getLong("campaign_id"), EntitlementStatus.valueOf(rs.getString("status")),
                    rs.getObject("valid_from", LocalDateTime.class),
                    rs.getObject("valid_until", LocalDateTime.class), rs.getLong("version"),
                    template.title(), template.benefitType(), template.productCode(),
                    template.benefitValueFen());
        };
    }

    private static EntitlementStatus nullableStatus(String value) {
        return value == null ? null : EntitlementStatus.valueOf(value);
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.stream().findFirst();
    }

    private static void requireChanged(int changed, String message) {
        if (changed != 1) throw new IllegalStateException(message);
    }
}
