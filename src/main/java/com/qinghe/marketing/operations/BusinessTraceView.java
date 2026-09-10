package com.qinghe.marketing.operations;

import java.util.Collections;
import java.util.List;

public final class BusinessTraceView {
    private final BusinessIdentifierType identifierType;
    private final String identifierValue;
    private final List<BusinessTraceNode> timeline;

    public BusinessTraceView(BusinessIdentifierType identifierType, String identifierValue,
                             List<BusinessTraceNode> timeline) {
        this.identifierType = identifierType;
        this.identifierValue = identifierValue;
        this.timeline = Collections.unmodifiableList(timeline);
    }

    public BusinessIdentifierType getIdentifierType() { return identifierType; }
    public String getIdentifierValue() { return identifierValue; }
    public List<BusinessTraceNode> getTimeline() { return timeline; }
}
