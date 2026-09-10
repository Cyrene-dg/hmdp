package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.store.StoreOwnershipType;

import java.time.LocalDateTime;

public final class PlatformOperationFact {
    private final Long redemptionId;
    private final Long reversalId;
    private final String redemptionNo;
    private final String posRequestNo;
    private final String posOrderNo;
    private final String storeCode;
    private final String terminalNo;
    private final String rightCodeHash;
    private final String operationStatus;
    private final String redemptionStatus;
    private final LocalDateTime occurredAt;
    private final StoreOwnershipType ownershipType;
    private final Long candidateId;
    private final Long subsidyFen;
    private final String candidateStatus;

    public PlatformOperationFact(Long redemptionId, Long reversalId, String redemptionNo,
                                 String posRequestNo, String posOrderNo, String storeCode,
                                 String terminalNo, String rightCodeHash, String operationStatus,
                                 String redemptionStatus, LocalDateTime occurredAt,
                                 StoreOwnershipType ownershipType, Long candidateId,
                                 Long subsidyFen, String candidateStatus) {
        this.redemptionId = redemptionId; this.reversalId = reversalId;
        this.redemptionNo = redemptionNo; this.posRequestNo = posRequestNo;
        this.posOrderNo = posOrderNo; this.storeCode = storeCode;
        this.terminalNo = terminalNo; this.rightCodeHash = rightCodeHash;
        this.operationStatus = operationStatus; this.redemptionStatus = redemptionStatus;
        this.occurredAt = occurredAt; this.ownershipType = ownershipType;
        this.candidateId = candidateId; this.subsidyFen = subsidyFen;
        this.candidateStatus = candidateStatus;
    }

    public Long redemptionId() { return redemptionId; }
    public Long reversalId() { return reversalId; }
    public String redemptionNo() { return redemptionNo; }
    public String posRequestNo() { return posRequestNo; }
    public String posOrderNo() { return posOrderNo; }
    public String storeCode() { return storeCode; }
    public String terminalNo() { return terminalNo; }
    public String rightCodeHash() { return rightCodeHash; }
    public String operationStatus() { return operationStatus; }
    public String redemptionStatus() { return redemptionStatus; }
    public LocalDateTime occurredAt() { return occurredAt; }
    public StoreOwnershipType ownershipType() { return ownershipType; }
    public Long candidateId() { return candidateId; }
    public Long subsidyFen() { return subsidyFen; }
    public String candidateStatus() { return candidateStatus; }
}
