package com.qinghe.marketing.campaign;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.store.StoreOwnershipType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcCampaignRepository implements CampaignRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcCampaignRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Campaign createDraft(String campaignNo, long templateId, CampaignDraftCommand command,
                                String createdBy, List<CampaignStoreSnapshot> stores, LocalDateTime now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO qh_campaign (campaign_no, template_id, name, description, status, "
                            + "begin_at, end_at, member_claim_limit, franchise_subsidy_fen, rule_version, "
                            + "version, created_by, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, 1, 0, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, campaignNo);
            statement.setLong(2, templateId);
            statement.setString(3, command.name());
            statement.setString(4, command.description());
            statement.setObject(5, command.claimBeginAt());
            statement.setObject(6, command.claimEndAt());
            statement.setInt(7, command.memberClaimLimit());
            if (command.franchiseSubsidyFen() == null) {
                statement.setNull(8, java.sql.Types.BIGINT);
            } else {
                statement.setLong(8, command.franchiseSubsidyFen());
            }
            statement.setString(9, createdBy);
            statement.setObject(10, now);
            statement.setObject(11, now);
            return statement;
        }, keys);
        long campaignId = keys.getKey().longValue();
        jdbcTemplate.update("INSERT INTO qh_campaign_inventory "
                        + "(campaign_id, total_stock, version, created_at, updated_at) VALUES (?, ?, 0, ?, ?)",
                campaignId, command.initialStock(), now, now);
        for (CampaignStoreSnapshot store : stores) {
            jdbcTemplate.update("INSERT INTO qh_campaign_store "
                            + "(campaign_id, store_id, store_type, subsidy_fen, participation_status, "
                            + "rule_version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    campaignId, store.storeId(), store.ownershipType().name(), store.subsidyFen(),
                    store.participationStatus(), store.ruleVersion(), now, now);
        }
        return requireCampaign(campaignId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Campaign updateDraft(long campaignId, long templateId, CampaignDraftCommand command,
                                String operatorId, List<CampaignStoreSnapshot> stores,
                                long expectedVersion, LocalDateTime now) {
        int updated = jdbcTemplate.update("UPDATE qh_campaign SET template_id = ?, name = ?, description = ?, "
                        + "status = 'DRAFT', begin_at = ?, end_at = ?, member_claim_limit = ?, "
                        + "franchise_subsidy_fen = ?, rule_version = rule_version + 1, "
                        + "version = version + 1, updated_at = ? WHERE id = ? AND created_by = ? "
                        + "AND status IN ('DRAFT', 'REJECTED') AND version = ?",
                templateId, command.name(), command.description(), command.claimBeginAt(), command.claimEndAt(),
                command.memberClaimLimit(), command.franchiseSubsidyFen(), now,
                campaignId, operatorId, expectedVersion);
        if (updated != 1) {
            throw new QingheBusinessException(QingheErrorCode.VERSION_CONFLICT,
                    "campaign status or version changed");
        }
        int inventoryUpdated = jdbcTemplate.update("UPDATE qh_campaign_inventory SET total_stock = ?, "
                        + "version = version + 1, updated_at = ? WHERE campaign_id = ?",
                command.initialStock(), now, campaignId);
        if (inventoryUpdated != 1) {
            throw new QingheBusinessException(QingheErrorCode.CAMPAIGN_INCOMPLETE,
                    "campaign inventory does not exist");
        }
        jdbcTemplate.update("DELETE FROM qh_campaign_store WHERE campaign_id = ?", campaignId);
        Campaign refreshed = requireCampaign(campaignId);
        for (CampaignStoreSnapshot store : stores) {
            jdbcTemplate.update("INSERT INTO qh_campaign_store "
                            + "(campaign_id, store_id, store_type, subsidy_fen, participation_status, "
                            + "rule_version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    campaignId, store.storeId(), store.ownershipType().name(), store.subsidyFen(),
                    store.participationStatus(), refreshed.ruleVersion(), now, now);
        }
        return refreshed;
    }

    @Override
    public Optional<Campaign> findByCampaignNo(String campaignNo) {
        return find("campaign_no", campaignNo);
    }

    @Override
    public Optional<Campaign> findById(long campaignId) {
        return find("id", campaignId);
    }

    @Override
    public Optional<Campaign> findByIdForUpdate(long campaignId) {
        return find("id", campaignId, true);
    }

    @Override
    public List<Campaign> list(int offset, int limit) {
        return jdbcTemplate.query(
                "SELECT id, campaign_no, template_id, name, description, status, begin_at, end_at, "
                        + "member_claim_limit, franchise_subsidy_fen, rule_version, version, created_by "
                        + "FROM qh_campaign ORDER BY id DESC LIMIT ? OFFSET ?",
                new Object[]{limit, offset}, (resultSet, rowNum) -> campaign(resultSet));
    }

    private Optional<Campaign> find(String column, Object value) {
        return find(column, value, false);
    }

    private Optional<Campaign> find(String column, Object value, boolean forUpdate) {
        List<Campaign> rows = jdbcTemplate.query(
                "SELECT id, campaign_no, template_id, name, description, status, begin_at, end_at, "
                        + "member_claim_limit, franchise_subsidy_fen, rule_version, version, created_by "
                        + "FROM qh_campaign WHERE " + column + " = ?" + (forUpdate ? " FOR UPDATE" : ""),
                new Object[]{value}, (resultSet, rowNum) -> campaign(resultSet));
        return rows.stream().findFirst();
    }

    @Override
    public CampaignInventory requireInventory(long campaignId) {
        List<CampaignInventory> rows = jdbcTemplate.query(
                "SELECT campaign_id, total_stock, version FROM qh_campaign_inventory WHERE campaign_id = ?",
                new Object[]{campaignId}, (resultSet, rowNum) -> new CampaignInventory(
                        resultSet.getLong("campaign_id"), resultSet.getLong("total_stock"),
                        resultSet.getLong("version")));
        return rows.stream().findFirst().orElseThrow(() -> new QingheBusinessException(
                QingheErrorCode.CAMPAIGN_INCOMPLETE, "campaign inventory does not exist"));
    }

    @Override
    public List<CampaignStoreSnapshot> findStores(long campaignId) {
        return jdbcTemplate.query("SELECT cs.store_id, s.external_store_code, cs.store_type, "
                        + "cs.subsidy_fen, cs.participation_status, cs.rule_version "
                        + "FROM qh_campaign_store cs JOIN qh_store s ON s.id = cs.store_id "
                        + "WHERE cs.campaign_id = ? ORDER BY cs.store_id",
                new Object[]{campaignId}, (resultSet, rowNum) -> new CampaignStoreSnapshot(
                        resultSet.getLong("store_id"), resultSet.getString("external_store_code"),
                        StoreOwnershipType.valueOf(resultSet.getString("store_type")),
                        resultSet.getLong("subsidy_fen"), resultSet.getString("participation_status"),
                        resultSet.getLong("rule_version")));
    }

    @Override
    public Campaign transition(long campaignId, CampaignStatus expectedStatus, CampaignStatus nextStatus,
                               long expectedVersion, String approvedBy, LocalDateTime now) {
        int updated;
        if (nextStatus == CampaignStatus.PENDING_APPROVAL) {
            updated = jdbcTemplate.update("UPDATE qh_campaign SET status = ?, submitted_at = ?, "
                            + "version = version + 1, updated_at = ? "
                            + "WHERE id = ? AND status = ? AND version = ?",
                    nextStatus.name(), now, now, campaignId, expectedStatus.name(), expectedVersion);
        } else if (nextStatus == CampaignStatus.SCHEDULED) {
            updated = jdbcTemplate.update("UPDATE qh_campaign SET status = ?, approved_by = ?, "
                            + "scheduled_at = ?, version = version + 1, updated_at = ? "
                            + "WHERE id = ? AND status = ? AND version = ?",
                    nextStatus.name(), approvedBy, now, now, campaignId, expectedStatus.name(), expectedVersion);
        } else {
            updated = jdbcTemplate.update("UPDATE qh_campaign SET status = ?, "
                            + "version = version + 1, updated_at = ? "
                            + "WHERE id = ? AND status = ? AND version = ?",
                    nextStatus.name(), now, campaignId, expectedStatus.name(), expectedVersion);
        }
        if (updated != 1) {
            throw new QingheBusinessException(QingheErrorCode.VERSION_CONFLICT,
                    "campaign status or version changed");
        }
        return requireCampaign(campaignId);
    }

    @Override
    public void saveReview(CampaignReview review) {
        jdbcTemplate.update("INSERT INTO qh_campaign_review "
                        + "(review_no, campaign_id, decision, applicant_id, reviewer_id, before_status, "
                        + "after_status, comment, reviewed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                review.reviewNo(), review.campaignId(), review.decision().name(), review.applicantId(),
                review.reviewerId(), review.beforeStatus().name(), review.afterStatus().name(),
                review.comment(), review.reviewedAt());
    }

    @Override
    public void savePublication(CampaignPublicationSnapshot snapshot) {
        jdbcTemplate.update("INSERT INTO qh_campaign_publication_snapshot "
                        + "(campaign_id, rule_version, template_snapshot, claim_begin_at, claim_end_at, "
                        + "member_claim_limit, initial_stock, franchise_subsidy_fen, published_by, published_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                snapshot.campaignId(), snapshot.ruleVersion(), snapshot.templateSnapshotJson(),
                snapshot.claimBeginAt(), snapshot.claimEndAt(), snapshot.memberClaimLimit(),
                snapshot.initialStock(), snapshot.franchiseSubsidyFen(), snapshot.publishedBy(),
                snapshot.publishedAt());
    }

    @Override
    public Optional<CampaignPublicationSnapshot> findPublication(long campaignId) {
        List<CampaignPublicationSnapshot> rows = jdbcTemplate.query(
                "SELECT campaign_id, rule_version, template_snapshot, claim_begin_at, claim_end_at, "
                        + "member_claim_limit, initial_stock, franchise_subsidy_fen, published_by, published_at "
                        + "FROM qh_campaign_publication_snapshot WHERE campaign_id = ?",
                new Object[]{campaignId}, (resultSet, rowNum) -> new CampaignPublicationSnapshot(
                        resultSet.getLong("campaign_id"), resultSet.getLong("rule_version"),
                        resultSet.getString("template_snapshot"),
                        resultSet.getObject("claim_begin_at", LocalDateTime.class),
                        resultSet.getObject("claim_end_at", LocalDateTime.class),
                        resultSet.getInt("member_claim_limit"), resultSet.getLong("initial_stock"),
                        nullableLong(resultSet, "franchise_subsidy_fen"),
                        resultSet.getString("published_by"),
                        resultSet.getObject("published_at", LocalDateTime.class)));
        return rows.stream().findFirst();
    }

    @Override
    public Campaign terminate(long campaignId, long expectedVersion, String operatorId,
                              String reason, LocalDateTime now) {
        int updated = jdbcTemplate.update("UPDATE qh_campaign SET status = 'TERMINATED', terminated_by = ?, "
                        + "terminated_reason = ?, terminated_at = ?, version = version + 1, updated_at = ? "
                        + "WHERE id = ? AND status = 'ACTIVE' AND version = ?",
                operatorId, reason, now, now, campaignId, expectedVersion);
        if (updated != 1) {
            throw new QingheBusinessException(QingheErrorCode.VERSION_CONFLICT,
                    "campaign status or version changed");
        }
        return requireCampaign(campaignId);
    }

    @Override
    public int activateScheduled(LocalDateTime now) {
        return jdbcTemplate.update("UPDATE qh_campaign SET status = 'ACTIVE', version = version + 1, "
                        + "updated_at = ? WHERE status = 'SCHEDULED' AND begin_at <= ? AND end_at > ?",
                now, now, now);
    }

    @Override
    public int endActive(LocalDateTime now) {
        return jdbcTemplate.update("UPDATE qh_campaign SET status = 'ENDED', version = version + 1, "
                        + "updated_at = ? WHERE status = 'ACTIVE' AND end_at <= ?", now, now);
    }

    private Campaign requireCampaign(long campaignId) {
        return findById(campaignId).orElseThrow(() -> new QingheBusinessException(
                QingheErrorCode.RESOURCE_NOT_FOUND, "campaign does not exist"));
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Campaign campaign(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new Campaign(resultSet.getLong("id"), resultSet.getString("campaign_no"),
                resultSet.getLong("template_id"), resultSet.getString("name"),
                resultSet.getString("description"),
                CampaignStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("begin_at", LocalDateTime.class),
                resultSet.getObject("end_at", LocalDateTime.class),
                resultSet.getInt("member_claim_limit"),
                nullableLong(resultSet, "franchise_subsidy_fen"),
                resultSet.getLong("rule_version"), resultSet.getLong("version"),
                resultSet.getString("created_by"));
    }
}
