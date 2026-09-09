package com.qinghe.marketing.store;

import java.time.LocalDateTime;
import java.util.Optional;

public interface StoreImportRepository {

    Optional<StoreImportBatch> findBySourceVersion(String sourceVersion);

    Optional<StoreImportBatch> findByImportNo(String importNo);

    StoreImportBatch save(StoreImportBatch batch);

    void markCommitted(long batchId, LocalDateTime committedAt);
}
