package com.qinghe.marketing.entitlement;

public final class ClaimIssueResult {

    private final ClaimIssueOutcome outcome;
    private final MemberEntitlement entitlement;

    public ClaimIssueResult(ClaimIssueOutcome outcome, MemberEntitlement entitlement) {
        this.outcome = outcome;
        this.entitlement = entitlement;
    }

    public ClaimIssueOutcome outcome() { return outcome; }
    public MemberEntitlement entitlement() { return entitlement; }
}

