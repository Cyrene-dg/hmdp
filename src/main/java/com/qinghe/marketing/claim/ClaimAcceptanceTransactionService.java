package com.qinghe.marketing.claim;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ClaimAcceptanceTransactionService {

    private final ClaimRequestRepository claimRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ClaimAcceptanceTransactionService(ClaimRequestRepository claimRepository,
                                             OutboxEventRepository outboxRepository,
                                             ObjectMapper objectMapper) {
        this.claimRepository = claimRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public ClaimRequest accept(long campaignId, long memberId, String requestId,
                               String requestDigest, String reservationId,
                               String claimNo, String eventId, LocalDateTime now) {
        ClaimRequest claim = new ClaimRequest(0, claimNo, requestId, requestDigest,
                campaignId, memberId, claimCycle(campaignId), reservationId,
                ClaimStatus.PROCESSING, null, 0, now, now);
        ClaimRequest inserted = claimRepository.insert(claim);
        outboxRepository.insert(new OutboxEvent(eventId, "CLAIM_REQUEST", claimNo,
                "CLAIM_ACCEPTED", 1, payload(inserted, eventId), OutboxStatus.NEW, now));
        return inserted;
    }

    private String payload(ClaimRequest claim, String eventId) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("eventId", eventId);
        payload.put("claimNo", claim.claimNo());
        payload.put("reservationId", claim.reservationId());
        payload.put("campaignId", claim.campaignId());
        payload.put("memberId", claim.memberId());
        payload.put("requestId", claim.requestId());
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("claim event payload cannot be serialized", exception);
        }
    }

    static String claimCycle(long campaignId) {
        return "CAMPAIGN:" + campaignId;
    }
}
