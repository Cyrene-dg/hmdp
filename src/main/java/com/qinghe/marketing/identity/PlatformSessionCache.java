package com.qinghe.marketing.identity;

import java.util.Optional;

public interface PlatformSessionCache {

    Optional<AuthenticatedMember> find(String accessTokenHash);

    void put(String accessTokenHash, AuthenticatedMember member);

    static PlatformSessionCache noOp() {
        return new PlatformSessionCache() {
            @Override
            public Optional<AuthenticatedMember> find(String accessTokenHash) {
                return Optional.empty();
            }

            @Override
            public void put(String accessTokenHash, AuthenticatedMember member) {
                // Used only by narrow unit tests that intentionally exclude Redis.
            }
        };
    }
}
