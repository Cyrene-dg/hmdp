package com.qinghe.marketing.contract;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * P0 business behavior skeleton. These tests are deliberately disabled until WP-06 supplies
 * a real Qinghe redemption implementation. Their presence is not evidence that behavior passed.
 */
class PosBusinessContractSkeletonTest {

    @Test
    @Disabled("WP-06 not implemented: contract skeleton only")
    void sameRequestNumberAndDigestMustReturnTheFirstRedemptionResult() {
        fail("Implement against the real POS API in WP-06");
    }

    @Test
    @Disabled("WP-06 not implemented: contract skeleton only")
    void sameRequestNumberWithDifferentDigestMustReturnRequestConflict() {
        fail("Implement against the real POS API in WP-06");
    }

    @Test
    @Disabled("WP-06 not implemented: contract skeleton only")
    void concurrentRequestsForOneEntitlementMustHaveAtMostOneSuccess() {
        fail("Implement against the real POS API in WP-06");
    }

    @Test
    @Disabled("WP-06 not implemented: contract skeleton only")
    void aLostResponseMustBeRecoverableByTheOriginalRequestNumber() {
        fail("Implement against the real POS API in WP-06");
    }

    @Test
    @Disabled("WP-06 not implemented: contract skeleton only")
    void directStoreMustNotCreateSubsidyCandidateButFranchiseStoreMust() {
        fail("Implement against the real POS API in WP-06");
    }
}
