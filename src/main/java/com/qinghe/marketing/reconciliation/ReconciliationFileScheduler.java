package com.qinghe.marketing.reconciliation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name = "qinghe.reconciliation.file.scheduler-enabled",
        havingValue = "true")
public class ReconciliationFileScheduler {
    private static final Logger LOGGER=LoggerFactory.getLogger(
            ReconciliationFileScheduler.class);
    private final ReconciliationFileProcessingService processing;

    public ReconciliationFileScheduler(ReconciliationFileProcessingService processing) {
        this.processing = processing;
    }

    @Scheduled(fixedDelayString = "${qinghe.reconciliation.file.scan-delay-ms:60000}")
    public void scan() {
        for (ReconciliationFileProcessingResult result : processing.processAvailable()) {
            if (result.outcome()==ReconciliationFileOutcome.MISSING
                    || result.outcome()==ReconciliationFileOutcome.CONFLICT
                    || result.outcome()==ReconciliationFileOutcome.PARTIAL_FAILED
                    || result.outcome()==ReconciliationFileOutcome.FAILED) {
                LOGGER.warn("Qinghe reconciliation file requires attention: source={}, batch={}, "
                                + "outcome={}, errorCode={}",result.sourceName(),
                        result.reconBatchNo(),result.outcome(),result.errorCode());
            } else {
                LOGGER.info("Qinghe reconciliation file processed: source={}, batch={}, outcome={}",
                        result.sourceName(),result.reconBatchNo(),result.outcome());
            }
        }
    }
}
