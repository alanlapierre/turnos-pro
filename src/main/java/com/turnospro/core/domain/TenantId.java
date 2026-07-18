package com.turnospro.core.domain;

public record TenantId(String id) {

    public TenantId {
        if (id == null) {
            throw new IllegalArgumentException("tenant id cannot be null");
        }
    }
}
