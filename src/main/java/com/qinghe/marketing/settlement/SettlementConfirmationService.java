package com.qinghe.marketing.settlement;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SettlementConfirmationService {
    private final SettlementRepository repository;
    private final BusinessClock clock;

    public SettlementConfirmationService(SettlementRepository repository, BusinessClock clock) {
        this.repository = repository; this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public SettlementBatch confirm(String settlementBatchNo, SettlementConfirmCommand command) {
        validate(settlementBatchNo, command);
        SettlementBatch batch = repository.findByBatchNoForUpdate(settlementBatchNo)
                .orElseThrow(() -> new QingheBusinessException(
                        QingheErrorCode.RESOURCE_NOT_FOUND,
                        "settlement batch does not exist"));
        if (batch.status() == SettlementBatchStatus.CONFIRMED) {
            repository.insertAudit(command.operatorId(), "SETTLEMENT_CONFIRM_REPLAY",
                    "SETTLEMENT_BATCH", batch.batchNo(), batch.status().name(),
                    batch.status().name(), command.comment(), "IDEMPOTENT",
                    command.requestId(), clock.dateTime());
            return batch;
        }
        if (batch.status() != SettlementBatchStatus.PENDING_CONFIRM
                || batch.version() != command.expectedVersion()
                || batch.detailCount() != command.expectedDetailCount()
                || batch.totalFen() != command.expectedTotalFen()) {
            throw changed();
        }
        SettlementTotals actual = repository.lockAttachedEligibleDetails(batch.id());
        if (actual.detailCount() != batch.detailCount()
                || actual.totalFen() != batch.totalFen()) throw changed();

        LocalDateTime now = clock.dateTime();
        repository.confirmDetails(batch.id(), batch.detailCount(), now);
        repository.confirmBatch(batch.id(), batch.version(), command.operatorId(), now);
        SettlementBatch confirmed = repository.findByBatchNoForUpdate(settlementBatchNo)
                .orElseThrow(() -> new IllegalStateException(
                        "confirmed settlement batch cannot be reloaded"));
        repository.insertAudit(command.operatorId(), "SETTLEMENT_CONFIRM",
                "SETTLEMENT_BATCH", batch.batchNo(), batch.status().name(),
                confirmed.status().name(), command.comment(), "SUCCESS",
                command.requestId(), now);
        return confirmed;
    }

    private static QingheBusinessException changed() {
        return new QingheBusinessException(QingheErrorCode.SETTLEMENT_BATCH_CHANGED,
                "settlement batch version, count, amount or detail eligibility changed");
    }
    private static void validate(String batchNo, SettlementConfirmCommand command) {
        if (batchNo == null || !batchNo.matches("[A-Za-z0-9._:-]{8,64}")
                || command == null || command.expectedVersion() < 0
                || command.expectedDetailCount() < 0 || command.expectedTotalFen() < 0
                || blank(command.comment()) || command.comment().length() > 512
                || blank(command.operatorId()) || blank(command.requestId())) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "settlement confirmation request is invalid");
        }
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
