package com.qinghe.marketing.claim;

import com.qinghe.marketing.shared.clock.BusinessClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClaimReservationRecoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClaimReservationRecoveryService.class);

    private final ClaimRequestRepository claimRepository;
    private final ClaimReservationStore reservationStore;
    private final BusinessClock clock;

    public ClaimReservationRecoveryService(ClaimRequestRepository claimRepository,
                                           ClaimReservationStore reservationStore,
                                           BusinessClock clock) {
        this.claimRepository = claimRepository;
        this.reservationStore = reservationStore;
        this.clock = clock;
    }

    public RecoverySummary recoverCampaign(long campaignId, Duration orphanAge, int limit) {
        LocalDateTime cutoff = clock.dateTime().minus(orphanAge);
        List<ClaimReservationSnapshot> pending = reservationStore.findPendingBefore(
                campaignId, cutoff, limit);
        int linked = 0;
        int compensated = 0;
        for (ClaimReservationSnapshot reservation : pending) {
            if (claimRepository.findByReservationId(reservation.reservationId()).isPresent()) {
                reservationStore.markPersisted(campaignId, reservation.reservationId());
                linked++;
            } else {
                reservationStore.compensate(campaignId, reservation.memberId(), reservation.requestId(),
                        reservation.reservationId(), "ORPHAN_RESERVATION");
                compensated++;
                LOGGER.warn("Compensated orphan claim reservation, reservationId={}",
                        reservation.reservationId());
            }
        }
        return new RecoverySummary(pending.size(), linked, compensated);
    }

    public static final class RecoverySummary {
        private final int scanned;
        private final int linked;
        private final int compensated;
        private RecoverySummary(int scanned, int linked, int compensated) {
            this.scanned = scanned;
            this.linked = linked;
            this.compensated = compensated;
        }
        public int scanned() { return scanned; }
        public int linked() { return linked; }
        public int compensated() { return compensated; }
    }
}
