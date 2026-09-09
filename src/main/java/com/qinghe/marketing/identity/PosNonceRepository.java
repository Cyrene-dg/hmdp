package com.qinghe.marketing.identity;

import java.time.LocalDateTime;

public interface PosNonceRepository {

    boolean reserve(String clientId, String nonce, LocalDateTime expiresAt, LocalDateTime now);
}
