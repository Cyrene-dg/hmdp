package com.qinghe.marketing.identity;

public final class PosAuthenticationRequest {

    private final String method;
    private final String normalizedPath;
    private final String clientId;
    private final String timestamp;
    private final String nonce;
    private final String signature;
    private final byte[] rawBody;

    public PosAuthenticationRequest(String method, String normalizedPath, String clientId,
                                    String timestamp, String nonce, String signature, byte[] rawBody) {
        this.method = method;
        this.normalizedPath = normalizedPath;
        this.clientId = clientId;
        this.timestamp = timestamp;
        this.nonce = nonce;
        this.signature = signature;
        this.rawBody = rawBody == null ? new byte[0] : rawBody.clone();
    }

    public String method() { return method; }
    public String normalizedPath() { return normalizedPath; }
    public String clientId() { return clientId; }
    public String timestamp() { return timestamp; }
    public String nonce() { return nonce; }
    public String signature() { return signature; }
    public byte[] rawBody() { return rawBody.clone(); }
}
