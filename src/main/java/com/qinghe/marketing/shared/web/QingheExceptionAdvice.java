package com.qinghe.marketing.shared.web;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import javax.servlet.http.HttpServletRequest;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.qinghe.marketing")
public class QingheExceptionAdvice {

    private static final Logger LOGGER = LoggerFactory.getLogger(QingheExceptionAdvice.class);

    @ExceptionHandler(QingheBusinessException.class)
    public ResponseEntity<QingheErrorResponse> handleBusiness(QingheBusinessException exception,
                                                               HttpServletRequest request) {
        QingheErrorCode error = exception.errorCode();
        return ResponseEntity.status(error.httpStatus()).body(new QingheErrorResponse(
                publicCode(error), exception.getMessage(), QingheWebRequest.requestId(request),
                exception.originalRedemptionNo()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<QingheErrorResponse> handleMalformedRequest(Exception exception,
                                                                      HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new QingheErrorResponse(
                "INVALID_ARGUMENT", "request format is invalid", QingheWebRequest.requestId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<QingheErrorResponse> handleUnexpected(Exception exception,
                                                                 HttpServletRequest request) {
        String requestId = QingheWebRequest.requestId(request);
        LOGGER.error("Unexpected Qinghe API failure, requestId={}", requestId, exception);
        return ResponseEntity.status(500).body(new QingheErrorResponse(
                "INTERNAL_ERROR", "internal server error", requestId));
    }

    private static String publicCode(QingheErrorCode error) {
        if (error == QingheErrorCode.UNAUTHENTICATED) {
            return "UNAUTHORIZED";
        }
        if (error == QingheErrorCode.TEMPORARY_UNAVAILABLE
                || error == QingheErrorCode.EXTERNAL_SERVICE_UNAVAILABLE) {
            return "SYSTEM_BUSY";
        }
        if (error == QingheErrorCode.IDEMPOTENCY_CONFLICT
                || error == QingheErrorCode.BUSINESS_STATE_CONFLICT) {
            return "REQUEST_CONFLICT";
        }
        return error.name();
    }
}
