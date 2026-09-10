package com.qinghe.marketing.operations;

import java.time.LocalDateTime;
import java.util.List;

public interface OperationsQueryRepository {
    List<BusinessTraceNode> findTrace(BusinessIdentifierType type, String value);

    OperationalExceptionPage findExceptions(String exceptionType, String status,
                                             LocalDateTime stuckCutoff,
                                             LocalDateTime settlementCutoff,
                                             int pageNo, int pageSize);
}
