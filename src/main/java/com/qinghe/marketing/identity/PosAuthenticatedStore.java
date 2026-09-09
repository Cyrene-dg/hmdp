package com.qinghe.marketing.identity;

import com.qinghe.marketing.store.StoreOwnershipType;

public final class PosAuthenticatedStore {

    private final long storeId;
    private final String storeCode;
    private final StoreOwnershipType ownershipType;
    private final String clientId;

    public PosAuthenticatedStore(long storeId, String storeCode,
                                 StoreOwnershipType ownershipType, String clientId) {
        this.storeId = storeId;
        this.storeCode = storeCode;
        this.ownershipType = ownershipType;
        this.clientId = clientId;
    }

    public long storeId() { return storeId; }
    public String storeCode() { return storeCode; }
    public StoreOwnershipType ownershipType() { return ownershipType; }
    public String clientId() { return clientId; }
}
