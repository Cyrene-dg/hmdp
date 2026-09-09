package com.qinghe.marketing.identity;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PlatformSessionRepository {

    void save(PlatformSessionRecord session);

    Optional<AuthenticatedMember> findActiveMember(String accessTokenHash, LocalDateTime now);
}
