package com.qinghe.marketing.store;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.stereotype.Service;

@Service
public class StoreEligibilityService {

    private final StoreRepository storeRepository;

    public StoreEligibilityService(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    public StoreRecord requireActive(String externalStoreCode) {
        StoreRecord store = storeRepository.findByExternalStoreCode(externalStoreCode)
                .orElseThrow(() -> new QingheBusinessException(
                        QingheErrorCode.RESOURCE_NOT_FOUND, "store was not found"));
        if (!store.active()) {
            throw new QingheBusinessException(QingheErrorCode.STORE_NOT_ELIGIBLE,
                    "disabled store cannot perform new business operations");
        }
        return store;
    }
}
