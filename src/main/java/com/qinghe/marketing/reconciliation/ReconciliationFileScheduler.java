package com.qinghe.marketing.reconciliation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "qinghe.reconciliation.file.scheduler-enabled",
        havingValue = "true")
public class ReconciliationFileScheduler {
    private final ReconciliationFileProcessingService processing;

    public ReconciliationFileScheduler(ReconciliationFileProcessingService processing) {
        this.processing = processing;
    }

    @Scheduled(fixedDelayString = "${qinghe.reconciliation.file.scan-delay-ms:60000}")
    public void scan() {
        processing.processAvailable();
    }
}
