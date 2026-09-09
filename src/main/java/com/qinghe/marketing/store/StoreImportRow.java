package com.qinghe.marketing.store;

public final class StoreImportRow {

    private final int rowNumber;
    private final String sourceVersion;
    private final String externalStoreCode;
    private final String storeName;
    private final StoreOwnershipType ownershipType;
    private final StoreStatus storeStatus;
    private final String posVersion;
    private final String validationError;

    public StoreImportRow(int rowNumber, String sourceVersion, String externalStoreCode,
                          String storeName, StoreOwnershipType ownershipType,
                          StoreStatus storeStatus, String posVersion, String validationError) {
        this.rowNumber = rowNumber;
        this.sourceVersion = sourceVersion;
        this.externalStoreCode = externalStoreCode;
        this.storeName = storeName;
        this.ownershipType = ownershipType;
        this.storeStatus = storeStatus;
        this.posVersion = posVersion;
        this.validationError = validationError;
    }

    public int rowNumber() { return rowNumber; }
    public String sourceVersion() { return sourceVersion; }
    public String externalStoreCode() { return externalStoreCode; }
    public String storeName() { return storeName; }
    public StoreOwnershipType ownershipType() { return ownershipType; }
    public StoreStatus storeStatus() { return storeStatus; }
    public String posVersion() { return posVersion; }
    public String validationError() { return validationError; }

    public boolean valid() {
        return validationError == null;
    }
}
