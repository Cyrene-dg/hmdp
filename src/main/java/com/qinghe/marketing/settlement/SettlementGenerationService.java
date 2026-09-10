package com.qinghe.marketing.settlement;

import com.qinghe.marketing.reconciliation.ReconciliationBatch;
import com.qinghe.marketing.reconciliation.ReconciliationBatchStatus;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.audit.OperationAudit;
import com.qinghe.marketing.shared.audit.OperationAuditRecorder;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.id.BusinessIdType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class SettlementGenerationService {
    private final SettlementRepository repository;
    private final BusinessIdGenerator ids;
    private final BusinessClock clock;
    private final OperationAuditRecorder audits;

    public SettlementGenerationService(SettlementRepository repository,
                                       BusinessIdGenerator ids, BusinessClock clock,
                                       OperationAuditRecorder audits) {
        this.repository = repository; this.ids = ids; this.clock = clock; this.audits = audits;
    }

    @Transactional(rollbackFor = Exception.class)
    public SettlementGenerationResult generate(String reconBatchNo,
                                               SettlementGenerateCommand command) {
        validate(reconBatchNo, command);
        ReconciliationBatch recon = repository.findReconBatchForUpdate(reconBatchNo)
                .orElseThrow(() -> new QingheBusinessException(
                        QingheErrorCode.RESOURCE_NOT_FOUND,
                        "reconciliation batch does not exist"));
        Optional<SettlementBatch> selected = repository.findByReconBatchIdForUpdate(recon.id());
        if (selected.isPresent() && selected.get().status() != SettlementBatchStatus.DRAFT) {
            SettlementBatch existing = selected.get();
            audit(command, "SETTLEMENT_GENERATE_REPLAY",
                    "SETTLEMENT_BATCH", existing.batchNo(), existing.status().name(),
                    existing.status().name(), "IDEMPOTENT");
            return new SettlementGenerationResult(SettlementGenerationOutcome.ALREADY_EXISTS,
                    reconBatchNo, existing);
        }
        if (recon.status() != ReconciliationBatchStatus.COMPLETED) {
            throw new QingheBusinessException(QingheErrorCode.RECON_NOT_COMPLETED,
                    "reconciliation batch must be completed before settlement generation");
        }
        if (recon.version() != command.expectedReconVersion()) {
            throw new QingheBusinessException(QingheErrorCode.VERSION_CONFLICT,
                    "reconciliation batch version changed");
        }

        LocalDateTime now = clock.dateTime();
        SettlementBatch existingDraft = selected.orElse(null);
        SettlementTotals totals = repository.lockEligibleDetails(recon.id(),
                existingDraft == null ? null : existingDraft.id());
        if (totals.detailCount() == 0) {
            audit(command, "SETTLEMENT_GENERATE",
                    "RECON_BATCH", reconBatchNo, recon.status().name(), recon.status().name(),
                    "NO_SETTLEMENT_REQUIRED");
            return new SettlementGenerationResult(
                    SettlementGenerationOutcome.NO_SETTLEMENT_REQUIRED, reconBatchNo,
                    existingDraft);
        }
        if (existingDraft != null) {
            SettlementBatch rebuilt = repository.rebuildBatch(existingDraft, totals, now);
            audit(command, "SETTLEMENT_REBUILD",
                    "SETTLEMENT_BATCH", rebuilt.batchNo(), SettlementBatchStatus.DRAFT.name(),
                    rebuilt.status().name(), "SUCCESS");
            return new SettlementGenerationResult(SettlementGenerationOutcome.REBUILT,
                    reconBatchNo, rebuilt);
        }
        SettlementBatch created = repository.insertBatch(
                ids.next(BusinessIdType.SETTLEMENT_BATCH), recon, totals, now);
        repository.attachDetails(created.id(), totals.detailIds(), now);
        audit(command, "SETTLEMENT_GENERATE",
                "SETTLEMENT_BATCH", created.batchNo(), null, created.status().name(),
                "SUCCESS");
        return new SettlementGenerationResult(SettlementGenerationOutcome.CREATED,
                reconBatchNo, created);
    }

    private void audit(SettlementGenerateCommand command, String action, String businessType,
                       String businessId, String before, String after, String result) {
        audits.record(new OperationAudit("ADMIN", command.operatorId(), action, businessType,
                businessId, before, after, command.comment(), result, command.requestId(),
                command.requestId(), clock.instant()));
    }

    private static void validate(String reconBatchNo, SettlementGenerateCommand command) {
        if (reconBatchNo == null || !reconBatchNo.matches("[A-Za-z0-9._:-]{8,64}")
                || command == null || command.expectedReconVersion() < 0
                || blank(command.comment()) || command.comment().length() > 512
                || blank(command.operatorId()) || blank(command.requestId())) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "settlement generation request is invalid");
        }
    }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
