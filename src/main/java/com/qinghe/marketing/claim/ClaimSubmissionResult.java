package com.qinghe.marketing.claim;

public final class ClaimSubmissionResult {

    private final ClaimRequest claimRequest;
    private final boolean replay;

    public ClaimSubmissionResult(ClaimRequest claimRequest, boolean replay) {
        this.claimRequest = claimRequest;
        this.replay = replay;
    }

    public ClaimRequest claimRequest() { return claimRequest; }
    public boolean replay() { return replay; }
}
