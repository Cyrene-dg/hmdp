package com.qinghe.marketing.entitlement;

public interface RightCodeProtector {

    ProtectedRightCode protect(String plaintext);

    String reveal(byte[] encrypted);
}

