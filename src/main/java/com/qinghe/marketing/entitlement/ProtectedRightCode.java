package com.qinghe.marketing.entitlement;

public final class ProtectedRightCode {

    private final String hash;
    private final byte[] encrypted;

    public ProtectedRightCode(String hash, byte[] encrypted) {
        this.hash = hash;
        this.encrypted = encrypted.clone();
    }

    public String hash() { return hash; }
    public byte[] encrypted() { return encrypted.clone(); }
}

