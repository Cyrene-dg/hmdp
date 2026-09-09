package com.qinghe.marketing.entitlement;

import com.qinghe.marketing.claim.ClaimRequest;

public final class CompensationClaim {

    private final ClaimRequest claim;
    private final boolean actionable;

    public CompensationClaim(ClaimRequest claim, boolean actionable) {
        this.claim = claim;
        this.actionable = actionable;
    }

    public ClaimRequest claim() { return claim; }
    public boolean actionable() { return actionable; }
}

