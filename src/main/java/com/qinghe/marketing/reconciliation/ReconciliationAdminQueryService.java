package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ReconciliationAdminQueryService {
    private final ReconciliationAdminRepository repository;
    public ReconciliationAdminQueryService(ReconciliationAdminRepository repository) {
        this.repository=repository;
    }
    public ReconciliationPage list(LocalDate date, ReconciliationBatchStatus status,
                                   int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) throw invalid("page is invalid");
        return new ReconciliationPage(pageNo,pageSize,repository.count(date,status),
                repository.list(date,status,(pageNo-1)*pageSize,pageSize));
    }
    public ReconciliationBatchView require(String reconBatchNo) {
        if (reconBatchNo == null || !reconBatchNo.matches("[A-Za-z0-9._:-]{8,64}")) {
            throw invalid("reconciliation batch number is invalid");
        }
        return repository.findDetail(reconBatchNo).orElseThrow(() ->
                new QingheBusinessException(QingheErrorCode.RESOURCE_NOT_FOUND,
                        "reconciliation batch does not exist"));
    }
    private static QingheBusinessException invalid(String message) {
        return new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,message);
    }
}
