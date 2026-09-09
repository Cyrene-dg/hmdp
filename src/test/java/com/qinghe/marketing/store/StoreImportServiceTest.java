package com.qinghe.marketing.store;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreImportServiceTest {

    private final BusinessClock clock = () -> Instant.parse("2026-09-08T07:00:00Z");

    @Test
    void shouldPreviewAndCommitFiveDirectAndFiveFranchiseStores() {
        InMemoryImportRepository imports = new InMemoryImportRepository();
        InMemoryStoreRepository stores = new InMemoryStoreRepository();
        StoreImportService service = service(imports, stores);

        StoreImportPreview preview = service.preview(tenStoreCsv("STORE-20260908-01"), "operator-001");
        StoreImportPreview committed = service.commit(preview.importNo());

        assertEquals(StoreImportStatus.PREVIEWED, preview.status());
        assertEquals(10, preview.totalRows());
        assertEquals(10, preview.validRows());
        assertEquals(0, preview.errorRows());
        assertEquals(StoreImportStatus.COMMITTED, committed.status());
        assertEquals(10, stores.stores.size());
        assertEquals(5, stores.count(StoreOwnershipType.DIRECT));
        assertEquals(5, stores.count(StoreOwnershipType.FRANCHISE));
    }

    @Test
    void shouldReturnFirstPreviewForSameVersionAndDigest() {
        InMemoryImportRepository imports = new InMemoryImportRepository();
        InMemoryStoreRepository stores = new InMemoryStoreRepository();
        StoreImportService service = service(imports, stores);
        byte[] file = tenStoreCsv("STORE-20260908-01");

        StoreImportPreview first = service.preview(file, "operator-001");
        StoreImportPreview repeated = service.preview(file, "operator-002");

        assertEquals(first.importNo(), repeated.importNo());
        assertEquals(1, imports.byVersion.size());
    }

    @Test
    void shouldRejectSameVersionWithDifferentContent() {
        StoreImportService service = service(new InMemoryImportRepository(), new InMemoryStoreRepository());
        service.preview(tenStoreCsv("STORE-20260908-01"), "operator-001");
        byte[] changed = new String(tenStoreCsv("STORE-20260908-01"), StandardCharsets.UTF_8)
                .replace("青禾直营店1", "青禾更名直营店1").getBytes(StandardCharsets.UTF_8);

        QingheBusinessException exception = assertThrows(QingheBusinessException.class,
                () -> service.preview(changed, "operator-001"));

        assertEquals(QingheErrorCode.STORE_IMPORT_CONFLICT, exception.errorCode());
    }

    @Test
    void shouldPersistExplicitErrorRowsAndBlockCommit() {
        StoreImportService service = service(new InMemoryImportRepository(), new InMemoryStoreRepository());
        String file = header()
                + "STORE-ERROR-01,QH001,正常门店,DIRECT,ACTIVE,POS-3.2\n"
                + "STORE-ERROR-01,QH001,重复门店,UNKNOWN,ACTIVE,POS-3.2\n";

        StoreImportPreview preview = service.preview(file.getBytes(StandardCharsets.UTF_8), "operator-001");
        QingheBusinessException exception = assertThrows(QingheBusinessException.class,
                () -> service.commit(preview.importNo()));

        assertEquals(StoreImportStatus.REJECTED, preview.status());
        assertEquals(1, preview.errorRows());
        assertTrue(preview.errors().get(0).validationError().contains("duplicate store_code"));
        assertTrue(preview.errors().get(0).validationError().contains("ownership_type"));
        assertEquals(QingheErrorCode.BUSINESS_STATE_CONFLICT, exception.errorCode());
    }

    @Test
    void shouldSupportQuotedStoreNameContainingComma() {
        StoreImportService service = service(new InMemoryImportRepository(), new InMemoryStoreRepository());
        String file = header() + "STORE-QUOTE-01,QH001,\"青禾中心店,一层\",DIRECT,ACTIVE,POS-3.2\n";

        StoreImportPreview preview = service.preview(file.getBytes(StandardCharsets.UTF_8), "operator-001");

        assertEquals(1, preview.validRows());
        assertEquals(0, preview.errorRows());
    }

    private StoreImportService service(InMemoryImportRepository imports, InMemoryStoreRepository stores) {
        return new StoreImportService(imports, stores, new BusinessIdGenerator(clock), clock);
    }

    private static byte[] tenStoreCsv(String sourceVersion) {
        StringBuilder csv = new StringBuilder(header());
        for (int index = 1; index <= 10; index++) {
            String code = String.format("QH%03d", index);
            String ownership = index <= 5 ? "DIRECT" : "FRANCHISE";
            csv.append(sourceVersion).append(',').append(code).append(',')
                    .append(index <= 5 ? "青禾直营店" : "青禾加盟店").append(index).append(',')
                    .append(ownership).append(",ACTIVE,POS-3.2\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String header() {
        return "source_version,store_code,store_name,ownership_type,status,pos_version\n";
    }

    private static final class InMemoryImportRepository implements StoreImportRepository {
        private final Map<String, StoreImportBatch> byVersion = new LinkedHashMap<String, StoreImportBatch>();
        private final Map<String, StoreImportBatch> byNo = new LinkedHashMap<String, StoreImportBatch>();

        @Override
        public Optional<StoreImportBatch> findBySourceVersion(String sourceVersion) {
            return Optional.ofNullable(byVersion.get(sourceVersion));
        }

        @Override
        public Optional<StoreImportBatch> findByImportNo(String importNo) {
            return Optional.ofNullable(byNo.get(importNo));
        }

        @Override
        public StoreImportBatch save(StoreImportBatch batch) {
            StoreImportBatch saved = new StoreImportBatch(byVersion.size() + 1L, batch.importNo(),
                    batch.sourceVersion(), batch.fileSha256(), batch.status(), batch.createdBy(),
                    batch.createdAt(), null, batch.rows());
            byVersion.put(saved.sourceVersion(), saved);
            byNo.put(saved.importNo(), saved);
            return saved;
        }

        @Override
        public void markCommitted(long batchId, LocalDateTime committedAt) {
            StoreImportBatch current = byNo.values().stream()
                    .filter(batch -> batch.id() == batchId).findFirst().orElseThrow(IllegalStateException::new);
            StoreImportBatch committed = new StoreImportBatch(current.id(), current.importNo(),
                    current.sourceVersion(), current.fileSha256(), StoreImportStatus.COMMITTED,
                    current.createdBy(), current.createdAt(), committedAt, current.rows());
            byVersion.put(committed.sourceVersion(), committed);
            byNo.put(committed.importNo(), committed);
        }
    }

    private static final class InMemoryStoreRepository implements StoreRepository {
        private final Map<String, StoreRecord> stores = new LinkedHashMap<String, StoreRecord>();

        @Override
        public Optional<StoreRecord> findById(long id) {
            return stores.values().stream().filter(store -> store.id() == id).findFirst();
        }

        @Override
        public Optional<StoreRecord> findByExternalStoreCode(String externalStoreCode) {
            return Optional.ofNullable(stores.get(externalStoreCode));
        }

        @Override
        public void upsert(StoreImportRow row, LocalDateTime now) {
            StoreRecord previous = stores.get(row.externalStoreCode());
            stores.put(row.externalStoreCode(), new StoreRecord(previous == null ? stores.size() + 1L : previous.id(),
                    row.externalStoreCode(), row.storeName(), row.ownershipType(), row.storeStatus(),
                    row.posVersion(), row.sourceVersion(), previous == null ? 0L : previous.version() + 1L));
        }

        int count(StoreOwnershipType type) {
            return (int) stores.values().stream().filter(store -> store.ownershipType() == type).count();
        }
    }
}
