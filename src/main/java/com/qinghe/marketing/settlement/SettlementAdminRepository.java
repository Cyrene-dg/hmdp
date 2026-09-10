package com.qinghe.marketing.settlement;

import java.util.List;
import java.util.Optional;

public interface SettlementAdminRepository {
    List<SettlementBatchView> list(SettlementBatchStatus status,int offset,int limit);
    long count(SettlementBatchStatus status);
    Optional<SettlementBatchView> findDetail(String settlementBatchNo);
}
