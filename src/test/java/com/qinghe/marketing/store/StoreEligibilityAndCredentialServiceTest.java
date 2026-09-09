package com.qinghe.marketing.store;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreEligibilityAndCredentialServiceTest {

    private final BusinessClock clock = () -> Instant.parse("2026-09-08T07:00:00Z");

    @Test
    void shouldExposeOwnershipForLaterSubsidyBranch() {
        InMemoryStoreRepository stores = new InMemoryStoreRepository();
        stores.put(store(1L, "QH001", StoreOwnershipType.DIRECT, StoreStatus.ACTIVE));
        stores.put(store(2L, "QH006", StoreOwnershipType.FRANCHISE, StoreStatus.ACTIVE));
        StoreEligibilityService service = new StoreEligibilityService(stores);

        assertTrue(!service.requireActive("QH001").franchise());
        assertTrue(service.requireActive("QH006").franchise());
    }

    @Test
    void shouldRejectDisabledStoreForNewBusinessAndCredentialActivation() {
        InMemoryStoreRepository stores = new InMemoryStoreRepository();
        stores.put(store(1L, "QH001", StoreOwnershipType.DIRECT, StoreStatus.DISABLED));
        StoreEligibilityService eligibility = new StoreEligibilityService(stores);
        CapturingCredentialRepository credentials = new CapturingCredentialRepository();
        PosCredentialService service = new PosCredentialService(eligibility, credentials, clock);

        QingheBusinessException exception = assertThrows(QingheBusinessException.class,
                () -> service.activate("QH001", "pos-client-qh001", "vault://qinghe/pos/QH001", 1));

        assertEquals(QingheErrorCode.STORE_NOT_ELIGIBLE, exception.errorCode());
        assertEquals(null, credentials.credential);
    }

    @Test
    void shouldPersistOnlySecretReferenceForActiveStore() {
        InMemoryStoreRepository stores = new InMemoryStoreRepository();
        stores.put(store(6L, "QH006", StoreOwnershipType.FRANCHISE, StoreStatus.ACTIVE));
        CapturingCredentialRepository credentials = new CapturingCredentialRepository();
        PosCredentialService service = new PosCredentialService(
                new StoreEligibilityService(stores), credentials, clock);

        service.activate("QH006", "pos-client-qh006", "vault://qinghe/pos/QH006", 2);

        assertNotNull(credentials.credential);
        assertEquals(6L, credentials.credential.storeId());
        assertEquals("vault://qinghe/pos/QH006", credentials.credential.secretReference());
        assertEquals(2, credentials.credential.secretVersion());
    }

    @Test
    void shouldRejectPlainTextCredentialValue() {
        InMemoryStoreRepository stores = new InMemoryStoreRepository();
        stores.put(store(1L, "QH001", StoreOwnershipType.DIRECT, StoreStatus.ACTIVE));
        PosCredentialService service = new PosCredentialService(
                new StoreEligibilityService(stores), new CapturingCredentialRepository(), clock);

        QingheBusinessException exception = assertThrows(QingheBusinessException.class,
                () -> service.activate("QH001", "pos-client-qh001", "plain-secret-value", 1));

        assertEquals(QingheErrorCode.INVALID_ARGUMENT, exception.errorCode());
    }

    private static StoreRecord store(long id, String code, StoreOwnershipType ownership, StoreStatus status) {
        return new StoreRecord(id, code, "store-" + code, ownership, status,
                "POS-3.2", "STORE-001", 0L);
    }

    private static final class InMemoryStoreRepository implements StoreRepository {
        private final Map<String, StoreRecord> stores = new LinkedHashMap<String, StoreRecord>();

        void put(StoreRecord store) {
            stores.put(store.externalStoreCode(), store);
        }

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
            throw new UnsupportedOperationException();
        }
    }

    private static final class CapturingCredentialRepository implements PosCredentialRepository {
        private PosCredential credential;

        @Override
        public Optional<PosCredential> findActiveByClientId(String clientId) {
            return Optional.ofNullable(credential)
                    .filter(value -> value.clientId().equals(clientId));
        }

        @Override
        public void activate(PosCredential credential, LocalDateTime now) {
            this.credential = credential;
        }
    }
}
