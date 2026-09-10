package com.qinghe.marketing.redemption;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.identity.PosAuthenticationRequest;
import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.identity.PosRequestAuthenticator;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.web.QingheApiResponse;
import com.qinghe.marketing.shared.web.QingheWebRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@RestController
@RequestMapping("/openapi/v1")
public class PosRightsController {

    private static final String VERIFY_PATH = "/openapi/v1/rights/verify";
    private static final String REDEEM_PATH = "/openapi/v1/redemptions";

    private final ObjectMapper objectMapper;
    private final PosRequestAuthenticator authenticator;
    private final PosVerificationService verificationService;
    private final PosRedemptionService redemptionService;
    private final PosExecutionGuard executionGuard;

    public PosRightsController(ObjectMapper objectMapper, PosRequestAuthenticator authenticator,
                               PosVerificationService verificationService,
                               PosRedemptionService redemptionService,
                               PosExecutionGuard executionGuard) {
        this.objectMapper = objectMapper;
        this.authenticator = authenticator;
        this.verificationService = verificationService;
        this.redemptionService = redemptionService;
        this.executionGuard = executionGuard;
    }

    @PostMapping("/rights/verify")
    public QingheApiResponse<RightData> verify(
            @RequestBody byte[] rawBody,
            @RequestHeader("X-POS-Request-Id") String headerRequestNo,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-Timestamp") String timestamp,
            @RequestHeader("X-Nonce") String nonce,
            @RequestHeader("X-Signature") String signature,
            HttpServletRequest servletRequest) {
        QingheWebRequest.acceptPosRequestId(servletRequest, headerRequestNo);
        return executionGuard.execute("verify", () -> {
            PosAuthenticatedStore store = authenticate("POST", VERIFY_PATH, clientId, timestamp,
                    nonce, signature, rawBody);
            VerifyBody body = read(rawBody, VerifyBody.class,
                    "posRequestNo", "storeCode", "terminalNo", "rightCode", "requestedAt");
            requireHeaderMatches(headerRequestNo, body.posRequestNo);
            PosVerificationResult result = verificationService.verify(store,
                    new PosVerificationCommand(body.posRequestNo, body.storeCode, body.terminalNo,
                            body.rightCode, businessTime(body.requestedAt)));
            return QingheApiResponse.ok("right is available", headerRequestNo,
                    new RightData(result));
        });
    }

    @PostMapping("/redemptions")
    public QingheApiResponse<RedemptionData> redeem(
            @RequestBody byte[] rawBody,
            @RequestHeader("X-POS-Request-Id") String headerRequestNo,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-Timestamp") String timestamp,
            @RequestHeader("X-Nonce") String nonce,
            @RequestHeader("X-Signature") String signature,
            HttpServletRequest servletRequest) {
        QingheWebRequest.acceptPosRequestId(servletRequest, headerRequestNo);
        return executionGuard.execute("redeem", () -> {
            PosAuthenticatedStore store = authenticate("POST", REDEEM_PATH, clientId, timestamp,
                    nonce, signature, rawBody);
            RedeemBody body = read(rawBody, RedeemBody.class, "posRequestNo", "posOrderNo",
                    "storeCode", "terminalNo", "operatorNo", "rightCode", "occurredAt");
            requireHeaderMatches(headerRequestNo, body.posRequestNo);
            RedemptionResult result = redemptionService.redeem(store,
                    new PosRedemptionCommand(body.posRequestNo, body.posOrderNo, body.storeCode,
                            body.terminalNo, body.operatorNo, body.rightCode,
                            businessTime(body.occurredAt)));
            if (result.replay()) executionGuard.idempotentHit("redeem");
            return QingheApiResponse.ok("redemption succeeded", headerRequestNo,
                    new RedemptionData(result));
        });
    }

    @GetMapping("/redemptions/by-request/{posRequestNo}")
    public ResponseEntity<QingheApiResponse<RedemptionData>> query(
            @PathVariable String posRequestNo,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-Timestamp") String timestamp,
            @RequestHeader("X-Nonce") String nonce,
            @RequestHeader("X-Signature") String signature,
            HttpServletRequest servletRequest) {
        QingheWebRequest.acceptPosRequestId(servletRequest, posRequestNo);
        return executionGuard.execute("query", () -> {
            String path = "/openapi/v1/redemptions/by-request/" + posRequestNo;
            PosAuthenticatedStore store = authenticate("GET", path, clientId, timestamp,
                    nonce, signature, new byte[0]);
            RedemptionResult result = redemptionService.query(store, posRequestNo);
            HttpStatus status = result.status() == PosRequestStatus.PROCESSING
                    ? HttpStatus.ACCEPTED : HttpStatus.OK;
            return ResponseEntity.status(status).body(QingheApiResponse.ok(
                    "redemption result", posRequestNo, new RedemptionData(result)));
        });
    }

    private PosAuthenticatedStore authenticate(String method, String path, String clientId,
                                                String timestamp, String nonce, String signature,
                                                byte[] rawBody) {
        return authenticator.authenticate(new PosAuthenticationRequest(method, path, clientId,
                timestamp, nonce, signature, rawBody));
    }

    private <T> T read(byte[] rawBody, Class<T> type, String... fields) {
        try {
            JsonNode node = objectMapper.readTree(rawBody);
            if (node == null || !node.isObject()) throw new IllegalArgumentException("body is not an object");
            Set<String> allowed = new HashSet<String>(Arrays.asList(fields));
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                if (!allowed.contains(names.next())) throw new IllegalArgumentException("unknown field");
            }
            return objectMapper.treeToValue(node, type);
        } catch (Exception invalid) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "POS request body is invalid");
        }
    }

    private static void requireHeaderMatches(String headerRequestNo, String bodyRequestNo) {
        if (!headerRequestNo.equals(bodyRequestNo)) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "X-POS-Request-Id must equal body.posRequestNo");
        }
    }

    private static LocalDateTime businessTime(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(BusinessClock.BUSINESS_ZONE)
                    .toLocalDateTime();
        } catch (RuntimeException invalid) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "POS business time must be an offset date-time");
        }
    }

    private static OffsetDateTime responseTime(LocalDateTime value) {
        return value == null ? null : value.atZone(BusinessClock.BUSINESS_ZONE).toOffsetDateTime();
    }

    public static final class RightData {
        private final String rightNo;
        private final String title;
        private final String status;
        private final OffsetDateTime validUntil;
        private final String benefitType;
        private final String productCode;
        private final Long benefitValueFen;

        RightData(PosVerificationResult result) {
            this.rightNo = result.rightNo(); this.title = result.title();
            this.status = result.status().name(); this.validUntil = responseTime(result.validUntil());
            this.benefitType = result.benefitType().name(); this.productCode = result.productCode();
            this.benefitValueFen = result.benefitValueFen();
        }
        public String getRightNo() { return rightNo; }
        public String getTitle() { return title; }
        public String getStatus() { return status; }
        public OffsetDateTime getValidUntil() { return validUntil; }
        public String getBenefitType() { return benefitType; }
        public String getProductCode() { return productCode; }
        public Long getBenefitValueFen() { return benefitValueFen; }
    }

    public static final class RedemptionData {
        private final String posRequestNo;
        private final String redemptionNo;
        private final String status;
        private final OffsetDateTime firstProcessedAt;
        private final String rightStatus;
        private final String failureCode;

        RedemptionData(RedemptionResult result) {
            this.posRequestNo = result.posRequestNo(); this.redemptionNo = result.redemptionNo();
            this.status = result.status().name(); this.firstProcessedAt = responseTime(result.firstProcessedAt());
            this.rightStatus = result.rightStatus() == null ? null : result.rightStatus().name();
            this.failureCode = result.failureCode();
        }
        public String getPosRequestNo() { return posRequestNo; }
        public String getRedemptionNo() { return redemptionNo; }
        public String getStatus() { return status; }
        public OffsetDateTime getFirstProcessedAt() { return firstProcessedAt; }
        public String getRightStatus() { return rightStatus; }
        public String getFailureCode() { return failureCode; }
    }

    public static class VerifyBody {
        public String posRequestNo;
        public String storeCode;
        public String terminalNo;
        public String rightCode;
        public String requestedAt;
    }

    public static class RedeemBody {
        public String posRequestNo;
        public String posOrderNo;
        public String storeCode;
        public String terminalNo;
        public String operatorNo;
        public String rightCode;
        public String occurredAt;
    }
}
