package com.qinghe.marketing.entitlement;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface MemberEntitlementRepository {

    MemberEntitlement insert(MemberEntitlement entitlement);

    Optional<MemberEntitlement> findBySourceClaimId(long sourceClaimId);

    Optional<EntitlementView> findViewByNoAndMemberId(String entitlementNo, long memberId);

    List<EntitlementView> listViewsByMemberId(long memberId, EntitlementStatus status,
                                               int offset, int limit);

    long countByMemberId(long memberId, EntitlementStatus status);

    int expireAvailableBefore(LocalDateTime now);
}
