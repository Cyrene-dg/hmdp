package com.qinghe.marketing.campaign;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CampaignDraftCommand {

    private final String name;
    private final String description;
    private final String templateNo;
    private final LocalDateTime claimBeginAt;
    private final LocalDateTime claimEndAt;
    private final long initialStock;
    private final int memberClaimLimit;
    private final List<String> storeCodes;
    private final Long franchiseSubsidyFen;

    public CampaignDraftCommand(String name, String description, String templateNo,
                                LocalDateTime claimBeginAt, LocalDateTime claimEndAt,
                                long initialStock, int memberClaimLimit,
                                List<String> storeCodes, Long franchiseSubsidyFen) {
        this.name = name;
        this.description = description;
        this.templateNo = templateNo;
        this.claimBeginAt = claimBeginAt;
        this.claimEndAt = claimEndAt;
        this.initialStock = initialStock;
        this.memberClaimLimit = memberClaimLimit;
        this.storeCodes = storeCodes == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(storeCodes));
        this.franchiseSubsidyFen = franchiseSubsidyFen;
    }

    public String name() { return name; }
    public String description() { return description; }
    public String templateNo() { return templateNo; }
    public LocalDateTime claimBeginAt() { return claimBeginAt; }
    public LocalDateTime claimEndAt() { return claimEndAt; }
    public long initialStock() { return initialStock; }
    public int memberClaimLimit() { return memberClaimLimit; }
    public List<String> storeCodes() { return storeCodes; }
    public Long franchiseSubsidyFen() { return franchiseSubsidyFen; }
}
