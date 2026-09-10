package com.qinghe.marketing.operations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OperationsMetricsSnapshot {
    private final LocalDateTime generatedAt;
    private final String scope;
    private final Map<String, Long> claim;
    private final Map<String, Map<String, Long>> pos;
    private final Map<String, Long> reconciliation;
    private final Map<String, Long> persistentGauges;

    public OperationsMetricsSnapshot(LocalDateTime generatedAt, String scope,
                                     Map<String, Long> claim,
                                     Map<String, Map<String, Long>> pos,
                                     Map<String, Long> reconciliation,
                                     Map<String, Long> persistentGauges) {
        this.generatedAt = generatedAt;
        this.scope = scope;
        this.claim = immutable(claim);
        Map<String, Map<String, Long>> immutablePos =
                new LinkedHashMap<String, Map<String, Long>>();
        for (Map.Entry<String, Map<String, Long>> entry : pos.entrySet()) {
            immutablePos.put(entry.getKey(), immutable(entry.getValue()));
        }
        this.pos = Collections.unmodifiableMap(immutablePos);
        this.reconciliation = immutable(reconciliation);
        this.persistentGauges = immutable(persistentGauges);
    }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public String getScope() { return scope; }
    public Map<String, Long> getClaim() { return claim; }
    public Map<String, Map<String, Long>> getPos() { return pos; }
    public Map<String, Long> getReconciliation() { return reconciliation; }
    public Map<String, Long> getPersistentGauges() { return persistentGauges; }

    private static Map<String, Long> immutable(Map<String, Long> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Long>(source));
    }
}
