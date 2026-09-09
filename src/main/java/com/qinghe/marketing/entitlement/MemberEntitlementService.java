package com.qinghe.marketing.entitlement;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.stereotype.Service;

@Service
public class MemberEntitlementService {

    private final MemberEntitlementRepository repository;
    private final RightCodeProtector rightCodeProtector;

    public MemberEntitlementService(MemberEntitlementRepository repository,
                                    RightCodeProtector rightCodeProtector) {
        this.repository = repository;
        this.rightCodeProtector = rightCodeProtector;
    }

    public EntitlementPage list(long memberId, EntitlementStatus status, int pageNo, int pageSize) {
        requirePage(pageNo, pageSize);
        long calculatedOffset = (long) (pageNo - 1) * pageSize;
        if (calculatedOffset > Integer.MAX_VALUE) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "requested page is outside the supported range");
        }
        int offset = (int) calculatedOffset;
        return new EntitlementPage(repository.listViewsByMemberId(memberId, status, offset, pageSize),
                pageNo, pageSize, repository.countByMemberId(memberId, status));
    }

    public EntitlementView requireOwned(String entitlementNo, long memberId) {
        return repository.findViewByNoAndMemberId(entitlementNo, memberId)
                .orElseThrow(() -> new QingheBusinessException(QingheErrorCode.RESOURCE_NOT_FOUND,
                        "entitlement does not exist"));
    }

    public String protectedRightCode(EntitlementView view) {
        String plaintext = rightCodeProtector.reveal(view.entitlement().encryptedRightCode());
        int suffixLength = Math.min(6, plaintext.length());
        return "****" + plaintext.substring(plaintext.length() - suffixLength);
    }

    private static void requirePage(int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "pageNo must be positive and pageSize must be between 1 and 100");
        }
    }
}
