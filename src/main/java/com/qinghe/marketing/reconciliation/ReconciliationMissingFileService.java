package com.qinghe.marketing.reconciliation;

import com.qinghe.marketing.shared.clock.BusinessClock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class ReconciliationMissingFileService {
    private final ReconciliationFileParser parser;
    private final ReconciliationBatchRepository repository;
    private final BusinessClock clock;
    private final LocalTime deadline;

    public ReconciliationMissingFileService(ReconciliationFileParser parser,
            ReconciliationBatchRepository repository,BusinessClock clock,
            @Value("${qinghe.reconciliation.file.final-arrival-time:03:00}") String deadline) {
        this.parser=parser; this.repository=repository; this.clock=clock;
        this.deadline=LocalTime.parse(deadline);
    }

    @Transactional(rollbackFor=Exception.class)
    public ReconciliationFileOutcome observeMissingCsv(byte[] manifestBytes) {
        ReconciliationManifest manifest=parser.parseManifest(manifestBytes);
        LocalDateTime expectedBy=LocalDateTime.of(manifest.businessDate().plusDays(1),deadline);
        if (clock.dateTime().isBefore(expectedBy)) {
            return ReconciliationFileOutcome.WAITING_FOR_PAIR;
        }
        repository.recordMissingAttemptIfAbsent(manifest.provider(),manifest.batchNo(),
                manifest.checksum(),clock.dateTime());
        return ReconciliationFileOutcome.MISSING;
    }
}
