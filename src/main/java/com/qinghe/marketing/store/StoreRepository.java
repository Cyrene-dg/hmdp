package com.qinghe.marketing.store;

import java.time.LocalDateTime;
import java.util.Optional;

public interface StoreRepository {

    Optional<StoreRecord> findById(long id);

    Optional<StoreRecord> findByExternalStoreCode(String externalStoreCode);

    void upsert(StoreImportRow row, LocalDateTime now);
}
