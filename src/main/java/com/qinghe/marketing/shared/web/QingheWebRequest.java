package com.qinghe.marketing.shared.web;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import com.qinghe.marketing.shared.trace.TraceId;

import javax.servlet.http.HttpServletRequest;

public final class QingheWebRequest {

    private QingheWebRequest() {
    }

    public static String requestId(HttpServletRequest request) {
        Object accepted = request.getAttribute("qinghe.requestId");
        if (accepted instanceof String) {
            return (String) accepted;
        }
        String candidate = request.getHeader("X-Request-Id");
        if (!TraceId.isValid(candidate)) {
            candidate = request.getHeader("X-POS-Request-Id");
        }
        String requestId = TraceId.acceptOrCreate(candidate);
        request.setAttribute("qinghe.requestId", requestId);
        return requestId;
    }

    public static String acceptPosRequestId(HttpServletRequest request, String candidate) {
        if (!TraceId.isValid(candidate)) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "POS request number must match [A-Za-z0-9._:-]{8,64}");
        }
        request.setAttribute("qinghe.requestId", candidate);
        return candidate;
    }

    public static String requireRequestId(HttpServletRequest request) {
        String candidate = request.getHeader("X-Request-Id");
        if (!TraceId.isValid(candidate)) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "X-Request-Id must match [A-Za-z0-9._:-]{8,64}");
        }
        request.setAttribute("qinghe.requestId", candidate);
        return candidate;
    }
}
