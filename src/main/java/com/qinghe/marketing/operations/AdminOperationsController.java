package com.qinghe.marketing.operations;

import com.qinghe.marketing.identity.AdminAuthorizer;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.web.QingheApiResponse;
import com.qinghe.marketing.shared.web.QingheWebRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminOperationsController {
    private static final String OPERATION_READ = "operation:read";
    private final OperationsQueryService queries;
    private final OperationsMetricsService metrics;
    private final OrphanReservationInspectionService orphanReservations;
    private final AdminAuthorizer authorizer;

    public AdminOperationsController(OperationsQueryService queries,
                                     OperationsMetricsService metrics,
                                     OrphanReservationInspectionService orphanReservations,
                                     AdminAuthorizer authorizer) {
        this.queries = queries;
        this.metrics = metrics;
        this.orphanReservations = orphanReservations;
        this.authorizer = authorizer;
    }

    @GetMapping("/exceptions/redis-orphan-reservations")
    public QingheApiResponse<OrphanReservationInspection> orphanReservations(
            @RequestParam long campaignId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        authorizer.require(authorization, OPERATION_READ);
        return QingheApiResponse.ok("success", QingheWebRequest.requestId(request),
                orphanReservations.inspect(campaignId, limit));
    }

    @GetMapping("/business-traces")
    public QingheApiResponse<BusinessTraceView> trace(
            @RequestParam String identifierType,
            @RequestParam String identifierValue,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        authorizer.require(authorization, OPERATION_READ);
        return QingheApiResponse.ok("success", QingheWebRequest.requestId(request),
                queries.trace(type(identifierType), identifierValue));
    }

    @GetMapping("/exceptions")
    public QingheApiResponse<OperationalExceptionPage> exceptions(
            @RequestParam(required = false) String exceptionType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        authorizer.require(authorization, OPERATION_READ);
        return QingheApiResponse.ok("success", QingheWebRequest.requestId(request),
                queries.exceptions(exceptionType, status, pageNo, pageSize));
    }

    @GetMapping("/operations/metrics")
    public QingheApiResponse<OperationsMetricsSnapshot> metrics(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        authorizer.require(authorization, OPERATION_READ);
        return QingheApiResponse.ok("success", QingheWebRequest.requestId(request),
                metrics.snapshot());
    }

    private static BusinessIdentifierType type(String value) {
        if (value == null) throw invalid("identifierType is invalid");
        try {
            return BusinessIdentifierType.valueOf(value.trim());
        } catch (RuntimeException failure) {
            throw invalid("identifierType is invalid");
        }
    }

    private static QingheBusinessException invalid(String message) {
        return new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT, message);
    }
}
