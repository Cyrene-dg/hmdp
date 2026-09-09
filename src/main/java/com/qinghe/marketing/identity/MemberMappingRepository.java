package com.qinghe.marketing.identity;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MemberMappingRepository {

    Optional<MemberMapping> findByExternalMemberNo(String externalMemberNo);

    MemberMapping findOrCreate(String externalMemberNo, long candidatePlatformUserId,
                               MemberStatus statusSnapshot, LocalDateTime now);
}
