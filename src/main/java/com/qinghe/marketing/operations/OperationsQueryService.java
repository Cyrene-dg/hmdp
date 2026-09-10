package com.qinghe.marketing.operations;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Service
public class OperationsQueryService {
    private final OperationsQueryRepository repository;
    private final BusinessClock clock;
    private final Duration stuckAge;
    private final Duration settlementPendingAge;

    public OperationsQueryService(OperationsQueryRepository repository, BusinessClock clock,
                                  @Value("${qinghe.operations.stuck-seconds:300}") long stuckSeconds,
                                  @Value("${qinghe.operations.settlement-pending-seconds:86400}")
                                  long settlementPendingSeconds) {
        this.repository = repository;
        this.clock = clock;
        this.stuckAge = positiveDuration(stuckSeconds, "stuckSeconds");
        this.settlementPendingAge = positiveDuration(settlementPendingSeconds,
                "settlementPendingSeconds");
    }

    public BusinessTraceView trace(BusinessIdentifierType type, String value) {
        if (type == null) throw invalid("identifierType is required");
        String normalized = normalized(value, "identifierValue");
        List<BusinessTraceNode> timeline = repository.findTrace(type, normalized);
        if (timeline.isEmpty()) {
            throw new QingheBusinessException(QingheErrorCode.RESOURCE_NOT_FOUND,
                    "business trace was not found");
        }
        return new BusinessTraceView(type, normalized, timeline);
    }

    public OperationalExceptionPage exceptions(String exceptionType, String status,
                                                int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) {
            throw invalid("pageNo or pageSize is invalid");
        }
        return repository.findExceptions(optional(exceptionType), optional(status),
                clock.dateTime().minus(stuckAge),
                clock.dateTime().minus(settlementPendingAge), pageNo, pageSize);
    }

    private static String normalized(String value, String field) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > 128) {
            throw invalid(field + " is invalid");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.trim().isEmpty() ? null
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private static Duration positiveDuration(long seconds, String field) {
        if (seconds <= 0) throw new IllegalArgumentException(field + " must be positive");
        return Duration.ofSeconds(seconds);
    }

    private static QingheBusinessException invalid(String message) {
        return new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT, message);
    }
}
