package com.qinghe.marketing.store;

public final class StoreRecord {

    private final long id;
    private final String externalStoreCode;
    private final String name;
    private final StoreOwnershipType ownershipType;
    private final StoreStatus status;
    private final String posVersion;
    private final String sourceVersion;
    private final long version;

    public StoreRecord(long id, String externalStoreCode, String name,
                       StoreOwnershipType ownershipType, StoreStatus status,
                       String posVersion, String sourceVersion, long version) {
        this.id = id;
        this.externalStoreCode = externalStoreCode;
        this.name = name;
        this.ownershipType = ownershipType;
        this.status = status;
        this.posVersion = posVersion;
        this.sourceVersion = sourceVersion;
        this.version = version;
    }

    public long id() { return id; }
    public String externalStoreCode() { return externalStoreCode; }
    public String name() { return name; }
    public StoreOwnershipType ownershipType() { return ownershipType; }
    public StoreStatus status() { return status; }
    public String posVersion() { return posVersion; }
    public String sourceVersion() { return sourceVersion; }
    public long version() { return version; }

    public boolean active() {
        return status == StoreStatus.ACTIVE;
    }

    public boolean franchise() {
        return ownershipType == StoreOwnershipType.FRANCHISE;
    }
}
