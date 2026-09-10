package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.identity.AdminAuthorizer;
import com.qinghe.marketing.identity.AdminPrincipal;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.web.QingheApiResponse;
import com.qinghe.marketing.shared.web.QingheWebRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/recon-batches")
public class AdminReconciliationController {
    private static final String RECON_OPERATE="recon:operate";
    private final ReconciliationAdminQueryService queries;
    private final ReconciliationRetryService retries;
    private final AdminAuthorizer authorizer;

    public AdminReconciliationController(ReconciliationAdminQueryService queries,
            ReconciliationRetryService retries,AdminAuthorizer authorizer) {
        this.queries=queries; this.retries=retries; this.authorizer=authorizer;
    }

    @GetMapping
    public QingheApiResponse<ReconciliationPage> list(
            @RequestParam(required=false) String businessDate,
            @RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") int pageNo,
            @RequestParam(defaultValue="20") int pageSize,
            @RequestHeader(value="Authorization",required=false) String authorization,
            HttpServletRequest request) {
        authorizer.require(authorization,RECON_OPERATE);
        return QingheApiResponse.ok("success",QingheWebRequest.requestId(request),
                queries.list(date(businessDate),status(status),pageNo,pageSize));
    }

    @GetMapping("/{reconBatchNo}")
    public QingheApiResponse<ReconciliationBatchView> get(@PathVariable String reconBatchNo,
            @RequestHeader(value="Authorization",required=false) String authorization,
            HttpServletRequest request) {
        authorizer.require(authorization,RECON_OPERATE);
        return QingheApiResponse.ok("success",QingheWebRequest.requestId(request),
                queries.require(reconBatchNo));
    }

    @PostMapping("/{reconBatchNo}/retry")
    public QingheApiResponse<ReconciliationBatchView> retry(@PathVariable String reconBatchNo,
            @RequestBody VersionedCommentRequest body,
            @RequestHeader(value="Authorization",required=false) String authorization,
            HttpServletRequest request) {
        AdminPrincipal principal=authorizer.require(authorization,RECON_OPERATE);
        String requestId=QingheWebRequest.requireRequestId(request);
        if (body==null || body.expectedVersion==null) throw invalid("request body is invalid");
        return QingheApiResponse.ok("reconciliation retry completed",requestId,
                retries.retry(reconBatchNo,body.expectedVersion,body.comment,
                        principal.operatorId(),requestId));
    }

    private static LocalDate date(String value) {
        if (value==null || value.isEmpty()) return null;
        try { return LocalDate.parse(value); }
        catch (RuntimeException failure) { throw invalid("businessDate is invalid"); }
    }
    private static ReconciliationBatchStatus status(String value) {
        if (value==null || value.isEmpty()) return null;
        try { return ReconciliationBatchStatus.valueOf(value); }
        catch (RuntimeException failure) { throw invalid("reconciliation status is invalid"); }
    }
    private static QingheBusinessException invalid(String message) {
        return new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,message);
    }
    public static final class VersionedCommentRequest {
        private Long expectedVersion; private String comment;
        public Long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(Long value) { expectedVersion=value; }
        public String getComment() { return comment; }
        public void setComment(String value) { comment=value; }
    }
}
