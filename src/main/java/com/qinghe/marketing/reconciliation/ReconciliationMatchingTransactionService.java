package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.store.StoreOwnershipType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ReconciliationMatchingTransactionService {
    private final ReconciliationMatchingRepository repository;
    private final BusinessClock clock;

    public ReconciliationMatchingTransactionService(ReconciliationMatchingRepository repository,
                                                    BusinessClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public int matchNext(long batchId, int limit) {
        ReconciliationBatch batch = repository.findBatchForUpdate(batchId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "reconciliation batch does not exist"));
        if (batch.status() != ReconciliationBatchStatus.MATCHING) return 0;
        List<ReconciliationRecord> records = repository.findUnmatchedForUpdate(batchId, limit);
        LocalDateTime now = clock.dateTime();
        for (ReconciliationRecord record : records) match(record, now);
        return records.size();
    }

    private void match(ReconciliationRecord record, LocalDateTime now) {
        Optional<PlatformOperationFact> selected = repository.findPlatformFact(record);
        if (!selected.isPresent()) {
            difference(record, null, ReconciliationMatchStatus.DIFFERENCE_POS_ONLY,
                    "POS row has no platform operation", now);
            return;
        }
        PlatformOperationFact fact = selected.get();
        if (repository.hasProcessedDuplicate(record)) {
            difference(record, fact, ReconciliationMatchStatus.DIFFERENCE_DATA,
                    "duplicate POS request in the same reconciliation batch", now);
            return;
        }
        if (!record.operationStatus().equals(fact.operationStatus())) {
            difference(record, fact, ReconciliationMatchStatus.DIFFERENCE_STATUS,
                    "POS and platform operation status differ", now);
            return;
        }
        if (!sameBusinessData(record, fact)) {
            difference(record, fact, ReconciliationMatchStatus.DIFFERENCE_DATA,
                    "POS and platform business fields differ", now);
            return;
        }
        if ("FAILED".equals(record.operationStatus())) {
            repository.markRecord(record.id(), ReconciliationMatchStatus.MATCHED,
                    "matching failed operation", fact, false, now);
            return;
        }
        if ("REVERSE".equals(record.operationType())) {
            repository.markRecord(record.id(), ReconciliationMatchStatus.MATCHED_REVERSAL,
                    "matching reversal fact", fact, false, now);
            return;
        }
        if (fact.ownershipType() == StoreOwnershipType.DIRECT) {
            repository.markRecord(record.id(), ReconciliationMatchStatus.MATCHED,
                    "direct store match requires no subsidy", fact, false, now);
            return;
        }
        if ("REVERSED".equals(fact.redemptionStatus())) {
            if (!"CANCELLED".equals(fact.candidateStatus())) {
                difference(record, fact, ReconciliationMatchStatus.DIFFERENCE_DATA,
                        "reversed franchise redemption has an active subsidy candidate", now);
                return;
            }
            repository.markRecord(record.id(), ReconciliationMatchStatus.MATCHED,
                    "reversed franchise redemption is not settlement eligible", fact, false, now);
            return;
        }
        if (fact.candidateId() == null || fact.subsidyFen() == null
                || "DIFFERENCE".equals(fact.candidateStatus())) {
            difference(record, fact, ReconciliationMatchStatus.DIFFERENCE_DATA,
                    "franchise redemption has no eligible subsidy candidate", now);
            return;
        }
        SettlementPreparationOutcome preparation = repository.prepareSettlementDetail(
                record.batchId(), fact, now);
        if (preparation == SettlementPreparationOutcome.ELIGIBLE) {
            repository.markRecord(record.id(), ReconciliationMatchStatus.MATCHED,
                    "franchise match created pending settlement detail", fact, true, now);
        } else if (preparation == SettlementPreparationOutcome.CANCELLED) {
            difference(record, fact, ReconciliationMatchStatus.DIFFERENCE_DATA,
                    "active franchise redemption has a cancelled subsidy candidate", now);
        } else {
            difference(record, fact, ReconciliationMatchStatus.DIFFERENCE_DATA,
                    "subsidy candidate is already bound to incompatible settlement detail", now);
        }
    }

    private void difference(ReconciliationRecord record, PlatformOperationFact fact,
                            ReconciliationMatchStatus type, String detail, LocalDateTime now) {
        repository.markCandidateDifference(fact == null ? null : fact.candidateId(), now);
        repository.markRecord(record.id(), type, detail, fact, false, now);
        repository.insertDifference(record.batchId(), record.id(),
                fact == null ? null : fact.redemptionId(),
                fact == null ? null : fact.reversalId(), type,
                "POS:" + record.operationType() + ":" + record.posRequestNo()
                        + ":" + record.lineNo(), detail, now);
    }

    private static boolean sameBusinessData(ReconciliationRecord record,
                                            PlatformOperationFact fact) {
        if (!Objects.equals(record.posRequestNo(), fact.posRequestNo())
                || !Objects.equals(record.storeCode(), fact.storeCode())) return false;
        if (record.redemptionNo() != null
                && !Objects.equals(record.redemptionNo(), fact.redemptionNo())) return false;
        if ("FAILED".equals(record.operationStatus())) return true;
        return Objects.equals(record.posOrderNo(), fact.posOrderNo())
                && Objects.equals(record.terminalNo(), fact.terminalNo())
                && Objects.equals(record.rightCodeHash(), fact.rightCodeHash())
                && Objects.equals(record.occurredAt(), fact.occurredAt());
    }
}
