package com.qinghe.marketing.identity;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AdminPrincipal {

    private final String operatorId;
    private final Set<String> permissions;

    public AdminPrincipal(String operatorId, Set<String> permissions) {
        this.operatorId = operatorId;
        this.permissions = Collections.unmodifiableSet(new LinkedHashSet<String>(permissions));
    }

    public String operatorId() { return operatorId; }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
