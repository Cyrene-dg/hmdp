package com.qinghe.marketing.campaign;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
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
public class JdbcInventoryAdjustmentRepository implements InventoryAdjustmentRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcInventoryAdjustmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public InventoryAdjustment create(String adjustmentNo, long campaignId, long incrementStock,
                                      String reason, String applicantId, LocalDateTime now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO qh_inventory_adjustment "
                            + "(adjustment_no, campaign_id, delta_stock, status, reason, applicant_id, "
                            + "version, created_at, updated_at) VALUES (?, ?, ?, 'DRAFT', ?, ?, 0, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, adjustmentNo);
            statement.setLong(2, campaignId);
            statement.setLong(3, incrementStock);
            statement.setString(4, reason);
            statement.setString(5, applicantId);
            statement.setObject(6, now);
            statement.setObject(7, now);
            return statement;
        }, keys);
        return requireById(keys.getKey().longValue());
    }

    @Override
    public Optional<InventoryAdjustment> findByAdjustmentNo(String adjustmentNo) {
        return find("adjustment_no", adjustmentNo, false);
    }

    @Override
    public InventoryAdjustment submit(long adjustmentId, long expectedVersion, LocalDateTime now) {
        int updated = jdbcTemplate.update("UPDATE qh_inventory_adjustment SET status = 'PENDING_APPROVAL', "
                        + "submitted_at = ?, version = version + 1, updated_at = ? "
                        + "WHERE id = ? AND status = 'DRAFT' AND version = ?",
                now, now, adjustmentId, expectedVersion);
        requireUpdated(updated);
        return requireById(adjustmentId);
    }

    @Override
    public InventoryAdjustment reject(long adjustmentId, long expectedVersion, String reviewerId,
                                      String comment, LocalDateTime now) {
        int updated = jdbcTemplate.update("UPDATE qh_inventory_adjustment SET status = 'REJECTED', "
                        + "approver_id = ?, reviewed_at = ?, review_comment = ?, version = version + 1, "
                        + "updated_at = ? WHERE id = ? AND status = 'PENDING_APPROVAL' AND version = ?",
                reviewerId, now, comment, now, adjustmentId, expectedVersion);
        requireUpdated(updated);
        return requireById(adjustmentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryAdjustmentResult approveAndApply(long adjustmentId, long expectedVersion,
                                                     String reviewerId, String comment, LocalDateTime now) {
        InventoryAdjustment current = find("id", adjustmentId, true)
                .orElseThrow(() -> new QingheBusinessException(
                        QingheErrorCode.RESOURCE_NOT_FOUND, "inventory adjustment does not exist"));
        CampaignStatus campaignStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM qh_campaign WHERE id = ? FOR UPDATE",
                new Object[]{current.campaignId()},
                (resultSet, rowNum) -> CampaignStatus.valueOf(resultSet.getString("status")));
        if (current.status() == InventoryAdjustmentStatus.APPLIED) {
            long total = totalStock(current.campaignId());
            return new InventoryAdjustmentResult(current, total - current.incrementStock(), total);
        }
        if (current.version() != expectedVersion) {
            throw conflict("inventory adjustment version changed");
        }
        if (current.status() != InventoryAdjustmentStatus.PENDING_APPROVAL) {
            throw state("inventory adjustment is not pending approval");
        }
        if (campaignStatus != CampaignStatus.SCHEDULED && campaignStatus != CampaignStatus.ACTIVE) {
            throw state("campaign no longer allows inventory adjustment");
        }
        int approved = jdbcTemplate.update("UPDATE qh_inventory_adjustment SET status = 'APPROVED', "
                        + "approver_id = ?, reviewed_at = ?, review_comment = ?, version = version + 1, "
                        + "updated_at = ? WHERE id = ? AND status = 'PENDING_APPROVAL' AND version = ?",
                reviewerId, now, comment, now, adjustmentId, expectedVersion);
        requireUpdated(approved);
        long before = totalStock(current.campaignId());
        int inventoryUpdated = jdbcTemplate.update("UPDATE qh_campaign_inventory SET total_stock = total_stock + ?, "
                        + "version = version + 1, updated_at = ? WHERE campaign_id = ?",
                current.incrementStock(), now, current.campaignId());
        if (inventoryUpdated != 1) {
            throw new QingheBusinessException(QingheErrorCode.CAMPAIGN_INCOMPLETE,
                    "campaign inventory does not exist");
        }
        int applied = jdbcTemplate.update("UPDATE qh_inventory_adjustment SET status = 'APPLIED', "
                        + "applied_at = ?, version = version + 1, updated_at = ? "
                        + "WHERE id = ? AND status = 'APPROVED' AND version = ?",
                now, now, adjustmentId, expectedVersion + 1);
        requireUpdated(applied);
        return new InventoryAdjustmentResult(requireById(adjustmentId), before,
                Math.addExact(before, current.incrementStock()));
    }

    private Optional<InventoryAdjustment> find(String column, Object value, boolean forUpdate) {
        List<InventoryAdjustment> rows = jdbcTemplate.query(
                "SELECT id, adjustment_no, campaign_id, delta_stock, status, reason, applicant_id, "
                        + "approver_id, version FROM qh_inventory_adjustment WHERE " + column + " = ?"
                        + (forUpdate ? " FOR UPDATE" : ""),
                new Object[]{value}, (resultSet, rowNum) -> new InventoryAdjustment(
                        resultSet.getLong("id"), resultSet.getString("adjustment_no"),
                        resultSet.getLong("campaign_id"), resultSet.getLong("delta_stock"),
                        InventoryAdjustmentStatus.valueOf(resultSet.getString("status")),
                        resultSet.getString("reason"), resultSet.getString("applicant_id"),
                        resultSet.getString("approver_id"), resultSet.getLong("version")));
        return rows.stream().findFirst();
    }

    private InventoryAdjustment requireById(long id) {
        return find("id", id, false).orElseThrow(() -> new QingheBusinessException(
                QingheErrorCode.RESOURCE_NOT_FOUND, "inventory adjustment does not exist"));
    }

    private long totalStock(long campaignId) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT total_stock FROM qh_campaign_inventory WHERE campaign_id = ?",
                new Object[]{campaignId}, Long.class);
        if (value == null) {
            throw new QingheBusinessException(QingheErrorCode.CAMPAIGN_INCOMPLETE,
                    "campaign inventory does not exist");
        }
        return value;
    }

    private static void requireUpdated(int count) {
        if (count != 1) {
            throw conflict("inventory adjustment status or version changed");
        }
    }

    private static QingheBusinessException conflict(String message) {
        return new QingheBusinessException(QingheErrorCode.VERSION_CONFLICT, message);
    }

    private static QingheBusinessException state(String message) {
        return new QingheBusinessException(QingheErrorCode.CAMPAIGN_STATE_CONFLICT, message);
    }
}
