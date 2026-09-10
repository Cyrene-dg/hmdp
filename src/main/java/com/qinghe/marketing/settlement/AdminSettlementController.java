package com.qinghe.marketing.settlement;

import com.qinghe.marketing.identity.AdminAuthorizer;
import com.qinghe.marketing.identity.AdminPrincipal;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.web.QingheApiResponse;
import com.qinghe.marketing.shared.web.QingheWebRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSettlementController {
    private static final String SETTLEMENT_READ="settlement:read";
    private static final String SETTLEMENT_CONFIRM="settlement:confirm";
    private final SettlementGenerationService generation;
    private final SettlementConfirmationService confirmation;
    private final SettlementExportService exports;
    private final SettlementAdminQueryService queries;
    private final AdminAuthorizer authorizer;

    public AdminSettlementController(SettlementGenerationService generation,
            SettlementConfirmationService confirmation,SettlementExportService exports,
            SettlementAdminQueryService queries,AdminAuthorizer authorizer) {
        this.generation=generation; this.confirmation=confirmation; this.exports=exports;
        this.queries=queries; this.authorizer=authorizer;
    }

    @PostMapping("/recon-batches/{reconBatchNo}/settlement-batch")
    public QingheApiResponse<SettlementData> generate(@PathVariable String reconBatchNo,
            @RequestBody GenerateRequest body,
            @RequestHeader(value="Authorization",required=false) String authorization,
            HttpServletRequest request) {
        AdminPrincipal principal=authorizer.require(authorization,SETTLEMENT_READ);
        String requestId=QingheWebRequest.requireRequestId(request);
        if (body==null || body.expectedReconVersion==null) throw invalid("request body is invalid");
        SettlementGenerationResult result=generation.generate(reconBatchNo,
                new SettlementGenerateCommand(body.expectedReconVersion,body.comment,
                        principal.operatorId(),requestId));
        String code=result.outcome()==SettlementGenerationOutcome.NO_SETTLEMENT_REQUIRED
                ? "NO_SETTLEMENT_REQUIRED" : "OK";
        String message=result.outcome()==SettlementGenerationOutcome.NO_SETTLEMENT_REQUIRED
                ? "no settlement required" : "settlement batch generated";
        return QingheApiResponse.of(code,message,requestId,
                new SettlementData(result.reconBatchNo(),result.batch()));
    }

    @GetMapping("/settlement-batches")
    public QingheApiResponse<SettlementPage> list(@RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") int pageNo,
            @RequestParam(defaultValue="20") int pageSize,
            @RequestHeader(value="Authorization",required=false) String authorization,
            HttpServletRequest request) {
        authorizer.require(authorization,SETTLEMENT_READ);
        return QingheApiResponse.ok("success",QingheWebRequest.requestId(request),
                queries.list(status(status),pageNo,pageSize));
    }

    @GetMapping("/settlement-batches/{settlementBatchNo}")
    public QingheApiResponse<SettlementBatchView> get(@PathVariable String settlementBatchNo,
            @RequestHeader(value="Authorization",required=false) String authorization,
            HttpServletRequest request) {
        authorizer.require(authorization,SETTLEMENT_READ);
        return QingheApiResponse.ok("success",QingheWebRequest.requestId(request),
                queries.require(settlementBatchNo));
    }

    @PostMapping("/settlement-batches/{settlementBatchNo}/confirm")
    public QingheApiResponse<SettlementData> confirm(@PathVariable String settlementBatchNo,
            @RequestBody ConfirmRequest body,
            @RequestHeader(value="Authorization",required=false) String authorization,
            HttpServletRequest request) {
        AdminPrincipal principal=authorizer.require(authorization,SETTLEMENT_CONFIRM);
        String requestId=QingheWebRequest.requireRequestId(request);
        if (body==null || body.expectedVersion==null || body.expectedDetailCount==null
                || body.expectedTotalSubsidyFen==null) throw invalid("request body is invalid");
        SettlementBatch batch=confirmation.confirm(settlementBatchNo,new SettlementConfirmCommand(
                body.expectedVersion,body.expectedDetailCount,body.expectedTotalSubsidyFen,
                body.comment,principal.operatorId(),requestId));
        return QingheApiResponse.ok("settlement batch confirmed",requestId,
                new SettlementData(batch.reconBatchNo(),batch));
    }

    @GetMapping("/settlement-batches/{settlementBatchNo}/export")
    public ResponseEntity<byte[]> export(@PathVariable String settlementBatchNo,
            @RequestHeader(value="Authorization",required=false) String authorization,
            HttpServletRequest request) {
        AdminPrincipal principal=authorizer.require(authorization,SETTLEMENT_READ);
        String requestId=QingheWebRequest.requireRequestId(request);
        SettlementExport exported=exports.export(settlementBatchNo,principal.operatorId(),requestId);
        return ResponseEntity.ok()
                .contentType(new MediaType("text","csv",java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""+exported.fileName()+"\"")
                .header("X-Request-Id",requestId).body(exported.content());
    }

    private static SettlementBatchStatus status(String value) {
        if (value==null || value.isEmpty()) return null;
        try { return SettlementBatchStatus.valueOf(value); }
        catch (RuntimeException failure) { throw invalid("settlement status is invalid"); }
    }
    private static QingheBusinessException invalid(String message) {
        return new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,message);
    }
    public static final class GenerateRequest {
        private Long expectedReconVersion; private String comment;
        public Long getExpectedReconVersion() { return expectedReconVersion; }
        public void setExpectedReconVersion(Long value) { expectedReconVersion=value; }
        public String getComment() { return comment; }
        public void setComment(String value) { comment=value; }
    }
    public static final class ConfirmRequest {
        private Long expectedVersion; private Integer expectedDetailCount;
        private Long expectedTotalSubsidyFen; private String comment;
        public Long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(Long value) { expectedVersion=value; }
        public Integer getExpectedDetailCount() { return expectedDetailCount; }
        public void setExpectedDetailCount(Integer value) { expectedDetailCount=value; }
        public Long getExpectedTotalSubsidyFen() { return expectedTotalSubsidyFen; }
        public void setExpectedTotalSubsidyFen(Long value) { expectedTotalSubsidyFen=value; }
        public String getComment() { return comment; }
        public void setComment(String value) { comment=value; }
    }
    public static final class SettlementData {
        private final String reconBatchNo; private final SettlementBatch batch;
        private SettlementData(String reconBatchNo,SettlementBatch batch) {
            this.reconBatchNo=reconBatchNo; this.batch=batch;
        }
        public String getReconBatchNo() { return reconBatchNo; }
        public String getSettlementBatchNo() { return batch==null?null:batch.batchNo(); }
        public String getStatus() { return batch==null?null:batch.status().name(); }
        public int getDetailCount() { return batch==null?0:batch.detailCount(); }
        public int getStoreCount() { return batch==null?0:batch.storeCount(); }
        public long getTotalSubsidyFen() { return batch==null?0:batch.totalFen(); }
        public long getVersion() { return batch==null?0:batch.version(); }
        public String getConfirmedBy() { return batch==null?null:batch.confirmedBy(); }
        public java.time.LocalDateTime getConfirmedAt() { return batch==null?null:batch.confirmedAt(); }
    }
}
