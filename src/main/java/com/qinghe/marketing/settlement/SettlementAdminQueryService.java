package com.qinghe.marketing.settlement;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.stereotype.Service;

@Service
public class SettlementAdminQueryService {
    private final SettlementAdminRepository repository;
    public SettlementAdminQueryService(SettlementAdminRepository repository) {
        this.repository=repository;
    }
    public SettlementPage list(SettlementBatchStatus status,int pageNo,int pageSize) {
        if (pageNo<1 || pageSize<1 || pageSize>100) throw invalid("page is invalid");
        return new SettlementPage(pageNo,pageSize,repository.count(status),
                repository.list(status,(pageNo-1)*pageSize,pageSize));
    }
    public SettlementBatchView require(String batchNo) {
        if (batchNo==null || !batchNo.matches("[A-Za-z0-9._:-]{8,64}")) {
            throw invalid("settlement batch number is invalid");
        }
        return repository.findDetail(batchNo).orElseThrow(()->new QingheBusinessException(
                QingheErrorCode.RESOURCE_NOT_FOUND,"settlement batch does not exist"));
    }
    private static QingheBusinessException invalid(String message) {
        return new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,message);
    }
}
