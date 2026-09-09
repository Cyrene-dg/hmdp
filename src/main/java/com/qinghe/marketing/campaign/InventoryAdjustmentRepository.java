package com.qinghe.marketing.campaign;

import java.time.LocalDateTime;
import java.util.Optional;

public interface InventoryAdjustmentRepository {

    InventoryAdjustment create(String adjustmentNo, long campaignId, long incrementStock,
                               String reason, String applicantId, LocalDateTime now);

    Optional<InventoryAdjustment> findByAdjustmentNo(String adjustmentNo);

    InventoryAdjustment submit(long adjustmentId, long expectedVersion, LocalDateTime now);

    InventoryAdjustment reject(long adjustmentId, long expectedVersion, String reviewerId,
                               String comment, LocalDateTime now);

    InventoryAdjustmentResult approveAndApply(long adjustmentId, long expectedVersion,
                                              String reviewerId, String comment, LocalDateTime now);
}
