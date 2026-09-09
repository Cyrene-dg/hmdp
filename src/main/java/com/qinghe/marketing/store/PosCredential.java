package com.qinghe.marketing.store;

public final class PosCredential {

    private final long storeId;
    private final String clientId;
    private final String secretReference;
    private final int secretVersion;

    public PosCredential(long storeId, String clientId, String secretReference, int secretVersion) {
        this.storeId = storeId;
        this.clientId = clientId;
        this.secretReference = secretReference;
        this.secretVersion = secretVersion;
    }

    public long storeId() { return storeId; }
    public String clientId() { return clientId; }
    public String secretReference() { return secretReference; }
    public int secretVersion() { return secretVersion; }
}
