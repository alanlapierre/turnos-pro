package com.turnospro.infrastructure.security;

import com.turnospro.core.domain.TenantId;

import java.lang.ScopedValue;

@SuppressWarnings("preview")
public final class TenantContext {

    // Immutable key for virtual and platform threads (Java 21 ScopedValue)
    public static final ScopedValue<TenantId> TENANT_KEY = ScopedValue.newInstance();

    private TenantContext() {}

    /**
     * Returns the active TenantId within the current request Scope.
     */
    public static TenantId getRequiredTenantId() {
        if (!TENANT_KEY.isBound()) {
            throw new IllegalStateException("Unauthorized access: No Tenant context was bound for the current request.");
        }
        return TENANT_KEY.get();
    }

    /**
     * Returns true if the current execution is within a bound Tenant Scope.
     */
    public static boolean isBound() {
        return TENANT_KEY.isBound();
    }
}
