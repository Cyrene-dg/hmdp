package com.qinghe.marketing.redemption;

import com.qinghe.marketing.identity.PosAuthenticatedStore;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.stereotype.Service;

@Service
public class PosRedemptionService {

    private final RedemptionTransactionService transactionService;
    private final RedemptionRepository repository;

    public PosRedemptionService(RedemptionTransactionService transactionService,
                                RedemptionRepository repository) {
        this.transactionService = transactionService;
        this.repository = repository;
    }

    public RedemptionResult redeem(PosAuthenticatedStore store, PosRedemptionCommand command) {
        RedemptionResult result = transactionService.redeem(store, command);
        if (result.status() == PosRequestStatus.FAILED) throw failure(result);
        return result;
    }

    public RedemptionResult query(PosAuthenticatedStore store, String posRequestNo) {
        PosCommandValidator.validateRequestNo(posRequestNo);
        PosRedemptionRequest request = repository.findRequest(store.clientId(), posRequestNo)
                .orElseThrow(() -> new QingheBusinessException(
                        QingheErrorCode.REDEMPTION_NOT_FOUND,
                        "POS redemption request does not exist"));
        return RedemptionResult.from(request);
    }

    private static QingheBusinessException failure(RedemptionResult result) {
        QingheErrorCode code;
        try {
            code = QingheErrorCode.valueOf(result.failureCode());
        } catch (RuntimeException invalid) {
            code = QingheErrorCode.BUSINESS_STATE_CONFLICT;
        }
        return new QingheBusinessException(code, message(code), result.originalRedemptionNo());
    }

    private static String message(QingheErrorCode code) {
        switch (code) {
            case RIGHT_NOT_FOUND: return "right does not exist";
            case RIGHT_EXPIRED: return "right is expired";
            case STORE_NOT_APPLICABLE: return "right is not applicable to this store";
            case ALREADY_REDEEMED: return "right was already redeemed";
            default: return "right is not currently available";
        }
    }
}
