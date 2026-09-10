package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.shared.audit.OperationAudit;
import com.qinghe.marketing.shared.audit.OperationAuditRecorder;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ReconciliationRetryService {
    private final ReconciliationAdminQueryService queries;
    private final ReconciliationFileProcessingService files;
    private final ReconciliationMatchingService matching;
    private final OperationAuditRecorder audits;
    private final BusinessClock clock;
    private final int matchChunkSize;

    public ReconciliationRetryService(ReconciliationAdminQueryService queries,
            ReconciliationFileProcessingService files, ReconciliationMatchingService matching,
            OperationAuditRecorder audits, BusinessClock clock,
            @Value("${qinghe.reconciliation.match-chunk-size:200}") int matchChunkSize) {
        this.queries=queries; this.files=files; this.matching=matching; this.audits=audits;
        this.clock=clock; this.matchChunkSize=matchChunkSize;
    }

    public ReconciliationBatchView retry(String reconBatchNo,long expectedVersion,String comment,
                                         String operatorId,String requestId) {
        if (expectedVersion<0 || blank(comment) || comment.length()>512
                || blank(operatorId) || blank(requestId)) throw new QingheBusinessException(
                QingheErrorCode.INVALID_ARGUMENT,"reconciliation retry request is invalid");
        ReconciliationBatchView before=queries.require(reconBatchNo);
        if (before.version()!=expectedVersion) throw new QingheBusinessException(
                QingheErrorCode.VERSION_CONFLICT,"reconciliation batch version changed");
        String result;
        if (ReconciliationBatchStatus.COMPLETED.name().equals(before.status())) {
            result="IDEMPOTENT";
        } else if (ReconciliationBatchStatus.MATCHING.name().equals(before.status())) {
            matching.match(before.id(),matchChunkSize);
            result="SUCCESS";
        } else if (ReconciliationBatchStatus.RECEIVED.name().equals(before.status())
                || ReconciliationBatchStatus.IMPORTING.name().equals(before.status())) {
            ReconciliationFileProcessingResult retried=files.retry(before.fileName());
            if (retried.outcome()!=ReconciliationFileOutcome.COMPLETED
                    && retried.outcome()!=ReconciliationFileOutcome.DUPLICATE) {
                throw new QingheBusinessException(QingheErrorCode.BUSINESS_STATE_CONFLICT,
                        "reconciliation retry did not complete");
            }
            result="SUCCESS";
        } else if (ReconciliationBatchStatus.PARTIAL_FAILED.name().equals(before.status())) {
            throw new QingheBusinessException(QingheErrorCode.RECON_FILE_INVALID,
                    "bad rows require a correction batch and cannot be ignored by retry");
        } else {
            throw new QingheBusinessException(QingheErrorCode.RECON_BATCH_CONFLICT,
                    "conflicting reconciliation batch cannot be retried");
        }
        ReconciliationBatchView after=queries.require(reconBatchNo);
        audits.record(new OperationAudit("ADMIN",operatorId,"RECON_RETRY","RECON_BATCH",
                reconBatchNo,before.status(),after.status(),comment,result,requestId,requestId,
                clock.instant()));
        return after;
    }
    private static boolean blank(String value) { return value==null || value.trim().isEmpty(); }
}
