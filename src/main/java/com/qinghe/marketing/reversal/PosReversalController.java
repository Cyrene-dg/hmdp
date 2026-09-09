package com.qinghe.marketing.reversal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinghe.marketing.identity.PosAuthenticationRequest;
import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.identity.PosRequestAuthenticator;
import com.qinghe.marketing.redemption.PosExecutionGuard;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.web.QingheApiResponse;
import com.qinghe.marketing.shared.web.QingheWebRequest;
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
@RequestMapping("/openapi/v1/redemptions")
public class PosReversalController {
    private final ObjectMapper mapper;
    private final PosRequestAuthenticator authenticator;
    private final PosReversalService service;
    private final PosExecutionGuard guard;

    public PosReversalController(ObjectMapper mapper, PosRequestAuthenticator authenticator,
                                 PosReversalService service, PosExecutionGuard guard) {
        this.mapper = mapper; this.authenticator = authenticator;
        this.service = service; this.guard = guard;
    }

    @PostMapping("/{redemptionNo}/reversals")
    public QingheApiResponse<ReversalData> reverse(
            @PathVariable String redemptionNo, @RequestBody byte[] rawBody,
            @RequestHeader("X-POS-Request-Id") String headerRequestNo,
            @RequestHeader("X-Client-Id") String clientId,
            @RequestHeader("X-Timestamp") String timestamp,
            @RequestHeader("X-Nonce") String nonce,
            @RequestHeader("X-Signature") String signature,
            HttpServletRequest request) {
        QingheWebRequest.acceptPosRequestId(request, headerRequestNo);
        return guard.execute("reversal", () -> {
            String path = "/openapi/v1/redemptions/" + redemptionNo + "/reversals";
            PosAuthenticatedStore store = authenticator.authenticate(new PosAuthenticationRequest(
                    "POST", path, clientId, timestamp, nonce, signature, rawBody));
            Body body = read(rawBody);
            if (!headerRequestNo.equals(body.posRequestNo)) {
                throw invalid("X-POS-Request-Id must equal body.posRequestNo");
            }
            ReversalReason reason;
            try { reason = ReversalReason.valueOf(body.reasonCode); }
            catch (RuntimeException invalid) { throw invalid("reasonCode is invalid"); }
            ReversalResult result = service.reverse(store, new PosReversalCommand(redemptionNo,
                    body.posRequestNo, body.posOrderNo, body.storeCode, body.operatorNo, reason,
                    body.reasonRemark, businessTime(body.occurredAt)));
            return QingheApiResponse.ok("reversal succeeded", headerRequestNo,
                    new ReversalData(result));
        });
    }

    private Body read(byte[] rawBody) {
        try {
            JsonNode node = mapper.readTree(rawBody);
            if (node == null || !node.isObject()) throw new IllegalArgumentException();
            Set<String> allowed = new HashSet<String>(Arrays.asList("posRequestNo", "posOrderNo",
                    "storeCode", "operatorNo", "reasonCode", "reasonRemark", "occurredAt"));
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) if (!allowed.contains(names.next())) throw new IllegalArgumentException();
            return mapper.treeToValue(node, Body.class);
        } catch (Exception invalid) {
            throw invalid("POS reversal body is invalid");
        }
    }

    private static LocalDateTime businessTime(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(BusinessClock.BUSINESS_ZONE)
                    .toLocalDateTime();
        } catch (RuntimeException invalid) {
            throw invalid("occurredAt must be an offset date-time");
        }
    }
    private static QingheBusinessException invalid(String message) {
        return new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT, message);
    }

    public static final class ReversalData {
        private final String reversalNo;
        private final String redemptionNo;
        private final String status;
        private final String rightStatus;
        private final OffsetDateTime firstProcessedAt;
        ReversalData(ReversalResult result) {
            this.reversalNo = result.reversalNo(); this.redemptionNo = result.redemptionNo();
            this.status = ReversalStatus.SUCCESS.name(); this.rightStatus = result.rightStatus().name();
            this.firstProcessedAt = result.firstProcessedAt().atZone(BusinessClock.BUSINESS_ZONE)
                    .toOffsetDateTime();
        }
        public String getReversalNo() { return reversalNo; }
        public String getRedemptionNo() { return redemptionNo; }
        public String getStatus() { return status; }
        public String getRightStatus() { return rightStatus; }
        public OffsetDateTime getFirstProcessedAt() { return firstProcessedAt; }
    }

    public static class Body {
        public String posRequestNo;
        public String posOrderNo;
        public String storeCode;
        public String operatorNo;
        public String reasonCode;
        public String reasonRemark;
        public String occurredAt;
    }
}
