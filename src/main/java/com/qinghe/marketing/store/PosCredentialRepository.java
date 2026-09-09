package com.qinghe.marketing.store;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PosCredentialRepository {

    Optional<PosCredential> findActiveByClientId(String clientId);

    void activate(PosCredential credential, LocalDateTime now);
}
