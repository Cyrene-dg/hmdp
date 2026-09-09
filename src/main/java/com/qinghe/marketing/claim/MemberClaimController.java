package com.qinghe.marketing.claim;

import com.qinghe.marketing.identity.AuthenticatedMember;
import com.qinghe.marketing.identity.MemberAuthorizer;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.web.QingheApiResponse;
import com.qinghe.marketing.shared.web.QingheWebRequest;
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

@RestController
@RequestMapping("/api/v1/member")
public class MemberClaimController {

    private final ClaimService claimService;
    private final MemberAuthorizer authorizer;

    public MemberClaimController(ClaimService claimService, MemberAuthorizer authorizer) {
        this.claimService = claimService;
        this.authorizer = authorizer;
    }

    @PostMapping("/campaigns/{campaignNo}/claims")
    public ResponseEntity<QingheApiResponse<ClaimData>> submit(
            @PathVariable String campaignNo,
            @RequestBody SubmitClaimRequest body,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        AuthenticatedMember member = authorizer.require(authorization);
        String requestId = QingheWebRequest.requireRequestId(request);
        if (body == null || body.getClientRequestedAt() == null) {
            throw invalid("clientRequestedAt is required");
        }
        ClaimSubmissionResult result = claimService.submit(campaignNo, member.memberId(), requestId,
                parseDateTime(body.getClientRequestedAt()));
        QingheApiResponse<ClaimData> response = result.replay()
                ? QingheApiResponse.ok("existing claim result", requestId,
                new ClaimData(result.claimRequest(), true))
                : QingheApiResponse.accepted("claim accepted", requestId,
                new ClaimData(result.claimRequest(), true));
        return ResponseEntity.status(result.replay() ? 200 : 202).body(response);
    }

    @GetMapping("/claims/{claimNo}")
    public QingheApiResponse<ClaimData> get(
            @PathVariable String claimNo,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        AuthenticatedMember member = authorizer.require(authorization);
        return QingheApiResponse.ok("success", QingheWebRequest.requireRequestId(request),
                new ClaimData(claimService.requireOwnedClaim(claimNo, member.memberId()), false));
    }

    private static OffsetDateTime parseDateTime(String value) {
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException invalid) {
            throw invalid("clientRequestedAt must be ISO 8601 with an offset");
        }
    }

    private static QingheBusinessException invalid(String message) {
        return new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT, message);
    }

    public static final class SubmitClaimRequest {
        private String clientRequestedAt;
        public String getClientRequestedAt() { return clientRequestedAt; }
        public void setClientRequestedAt(String value) { this.clientRequestedAt = value; }
    }

    public static final class ClaimData {
        private final String claimNo;
        private final String status;
        private final Integer nextPollAfterMillis;
        private final String failureCode;
        private final LocalDateTime updatedAt;

        private ClaimData(ClaimRequest claim, boolean includePollingHint) {
            this.claimNo = claim.claimNo();
            this.status = claim.status().name();
            this.nextPollAfterMillis = includePollingHint
                    && (claim.status() == ClaimStatus.PROCESSING
                    || claim.status() == ClaimStatus.COMPENSATING) ? 500 : null;
            this.failureCode = claim.failureCode();
            this.updatedAt = claim.updatedAt();
        }

        public String getClaimNo() { return claimNo; }
        public String getStatus() { return status; }
        public Integer getNextPollAfterMillis() { return nextPollAfterMillis; }
        public String getFailureCode() { return failureCode; }
        public LocalDateTime getUpdatedAt() { return updatedAt; }
    }
}
