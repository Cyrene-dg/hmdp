package com.qinghe.marketing.reversal;

import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.stereotype.Service;

@Service
public class PosReversalService {
    private final ReversalTransactionService transactions;

    public PosReversalService(ReversalTransactionService transactions) {
        this.transactions = transactions;
    }

    public ReversalResult reverse(PosAuthenticatedStore store, PosReversalCommand command) {
        ReversalResult result = transactions.reverse(store, command);
        if (result.status() == ReversalRequestStatus.FAILED) throw failure(result);
        return result;
    }

    private static QingheBusinessException failure(ReversalResult result) {
        QingheErrorCode code;
        try {
            code = QingheErrorCode.valueOf(result.failureCode());
        } catch (RuntimeException invalid) {
            code = QingheErrorCode.BUSINESS_STATE_CONFLICT;
        }
        Integer status = code == QingheErrorCode.REDEMPTION_NOT_FOUND ? 409 : null;
        return new QingheBusinessException(code, message(code), result.redemptionNo(), status);
    }

    private static String message(QingheErrorCode code) {
        switch (code) {
            case REDEMPTION_NOT_FOUND: return "original redemption does not exist";
            case SETTLEMENT_LOCKED: return "settlement is locked and cannot be changed automatically";
            default: return "redemption is not eligible for automatic reversal";
        }
    }
}
