package com.qinghe.marketing.store;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class PosCredentialService {

    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final Pattern SECRET_REFERENCE = Pattern.compile("(vault|kms|secret)://.{3,240}");

    private final StoreEligibilityService storeEligibilityService;
    private final PosCredentialRepository credentialRepository;
    private final BusinessClock clock;

    public PosCredentialService(StoreEligibilityService storeEligibilityService,
                                PosCredentialRepository credentialRepository,
                                BusinessClock clock) {
        this.storeEligibilityService = storeEligibilityService;
        this.credentialRepository = credentialRepository;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    public void activate(String storeCode, String clientId, String secretReference, int secretVersion) {
        if (clientId == null || !CLIENT_ID.matcher(clientId).matches()) {
            throw invalid("invalid POS client id");
        }
        if (secretReference == null || !SECRET_REFERENCE.matcher(secretReference).matches()) {
            throw invalid("POS credential must use a secret-manager reference");
        }
        if (secretVersion <= 0) {
            throw invalid("POS secret version must be positive");
        }
        StoreRecord store = storeEligibilityService.requireActive(storeCode);
        credentialRepository.activate(new PosCredential(
                store.id(), clientId, secretReference, secretVersion), clock.dateTime());
    }

    private static QingheBusinessException invalid(String message) {
        return new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT, message);
    }
}
