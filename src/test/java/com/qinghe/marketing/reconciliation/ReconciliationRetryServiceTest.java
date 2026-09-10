package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.shared.audit.OperationAudit;
import com.qinghe.marketing.shared.audit.OperationAuditRecorder;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReconciliationRetryServiceTest {
    private static final String BATCH="REC202609090088";
    private static final BusinessClock CLOCK=()->Instant.parse("2026-09-09T03:00:00Z");

    @Test
    void shouldResumeMatchingAndRecordAuthenticatedAudit() {
        ReconciliationAdminQueryService queries=mock(ReconciliationAdminQueryService.class);
        ReconciliationFileProcessingService files=mock(ReconciliationFileProcessingService.class);
        ReconciliationMatchingService matching=mock(ReconciliationMatchingService.class);
        OperationAuditRecorder audits=mock(OperationAuditRecorder.class);
        when(queries.require(BATCH)).thenReturn(view("MATCHING",3),view("COMPLETED",4));
        ReconciliationRetryService service=new ReconciliationRetryService(
                queries,files,matching,audits,CLOCK,25);

        ReconciliationBatchView result=service.retry(BATCH,3,"恢复匹配","OPS-008",
                "recon-retry-001");

        assertEquals("COMPLETED",result.getStatus());
        verify(matching).match(88,25);
        ArgumentCaptor<OperationAudit> recorded=ArgumentCaptor.forClass(OperationAudit.class);
        verify(audits).record(recorded.capture());
        assertEquals("OPS-008",recorded.getValue().operatorId());
        assertEquals("recon-retry-001",recorded.getValue().requestId());
    }

    @Test
    void shouldTreatCompletedAsIdempotentAndRejectBadRows() {
        ReconciliationAdminQueryService queries=mock(ReconciliationAdminQueryService.class);
        ReconciliationFileProcessingService files=mock(ReconciliationFileProcessingService.class);
        ReconciliationMatchingService matching=mock(ReconciliationMatchingService.class);
        OperationAuditRecorder audits=mock(OperationAuditRecorder.class);
        when(queries.require(BATCH)).thenReturn(view("COMPLETED",4));
        ReconciliationRetryService service=new ReconciliationRetryService(
                queries,files,matching,audits,CLOCK,25);
        assertEquals("COMPLETED",service.retry(BATCH,4,"重复操作","OPS-008",
                "recon-retry-002").getStatus());
        verify(matching,never()).match(88,25);

        when(queries.require(BATCH)).thenReturn(view("PARTIAL_FAILED",5));
        QingheBusinessException failure=assertThrows(QingheBusinessException.class,
                ()->service.retry(BATCH,5,"忽略坏行","OPS-008","recon-retry-003"));
        assertEquals(QingheErrorCode.RECON_FILE_INVALID,failure.errorCode());
    }

    private static ReconciliationBatchView view(String status,long version) {
        return new ReconciliationBatchView(88,BATCH,"MOCK_POS","POSB20260908088",null,
                "checksum","POS_20260908_POSB20260908088.csv",status,1,1,0,1,0,0,1,
                null,version,null,null,null,null);
    }
}
