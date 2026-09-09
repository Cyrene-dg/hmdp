package com.qinghe.marketing.identity;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.web.QingheApiResponse;
import com.qinghe.marketing.shared.web.QingheWebRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/member/sessions")
public class MemberSessionController {

    private final MemberSessionService sessionService;

    public MemberSessionController(MemberSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/exchange")
    public QingheApiResponse<SessionData> exchange(@RequestBody SessionExchangeRequest body,
                                                    HttpServletRequest request) {
        String requestId = QingheWebRequest.requireRequestId(request);
        if (body == null) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT, "request body is required");
        }
        SessionExchangeResult result = sessionService.exchange(body.getMemberToken(), requestId);
        return QingheApiResponse.ok("success", requestId, new SessionData(result));
    }

    public static final class SessionExchangeRequest {
        private String memberToken;

        public String getMemberToken() { return memberToken; }
        public void setMemberToken(String memberToken) { this.memberToken = memberToken; }
    }

    public static final class SessionData {
        private final String accessToken;
        private final String tokenType = "Bearer";
        private final Instant expiresAt;
        private final MemberData member;

        private SessionData(SessionExchangeResult result) {
            this.accessToken = result.accessToken();
            this.expiresAt = result.expiresAt();
            this.member = new MemberData(result.maskedMemberNo(), result.levelCode());
        }

        public String getAccessToken() { return accessToken; }
        public String getTokenType() { return tokenType; }
        public Instant getExpiresAt() { return expiresAt; }
        public MemberData getMember() { return member; }
    }

    public static final class MemberData {
        private final String memberNoMasked;
        private final String levelCode;

        private MemberData(String memberNoMasked, String levelCode) {
            this.memberNoMasked = memberNoMasked;
            this.levelCode = levelCode;
        }

        public String getMemberNoMasked() { return memberNoMasked; }
        public String getLevelCode() { return levelCode; }
    }
}
