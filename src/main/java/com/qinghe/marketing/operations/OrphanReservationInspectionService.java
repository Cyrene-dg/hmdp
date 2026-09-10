package com.qinghe.marketing.operations;

import com.qinghe.marketing.claim.ClaimRequestRepository;
import com.qinghe.marketing.claim.ClaimReservationSnapshot;
import com.qinghe.marketing.claim.ClaimReservationStore;
import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class OrphanReservationInspectionService {
    private final ClaimReservationStore reservations;
    private final ClaimRequestRepository claims;
    private final BusinessClock clock;
    private final Duration orphanAge;

    public OrphanReservationInspectionService(
            ClaimReservationStore reservations,
            ClaimRequestRepository claims,
            BusinessClock clock,
            @Value("${qinghe.claim.recovery.orphan-seconds:120}") long orphanSeconds) {
        if (orphanSeconds <= 0) throw new IllegalArgumentException("orphanSeconds must be positive");
        this.reservations = reservations;
        this.claims = claims;
        this.clock = clock;
        this.orphanAge = Duration.ofSeconds(orphanSeconds);
    }

    public OrphanReservationInspection inspect(long campaignId, int limit) {
        if (campaignId <= 0 || limit < 1 || limit > 500) {
            throw new QingheBusinessException(QingheErrorCode.INVALID_ARGUMENT,
                    "campaignId or limit is invalid");
        }
        LocalDateTime cutoff = clock.dateTime().minus(orphanAge);
        List<ClaimReservationSnapshot> snapshots = reservations.findPendingBefore(
                campaignId, cutoff, limit + 1);
        boolean truncated = snapshots.size() > limit;
        List<OperationalExceptionView> items = new ArrayList<OperationalExceptionView>();
        int scanned = Math.min(snapshots.size(), limit);
        for (int index = 0; index < scanned; index++) {
            ClaimReservationSnapshot snapshot = snapshots.get(index);
            if (!claims.findByReservationId(snapshot.reservationId()).isPresent()) {
                items.add(new OperationalExceptionView("REDIS_ORPHAN_RESERVATION",
                        snapshot.reservationId(), "RESERVED", "CLAIM_NOT_PERSISTED",
                        snapshot.reservedAt(), "HIGH", Collections.singletonList("TRACE_ONLY")));
            }
        }
        return new OrphanReservationInspection(campaignId, cutoff, scanned, truncated, items);
    }
}
