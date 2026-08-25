package com.turnospro.core.domain;

import java.util.Objects;

public record TenantId(String id) {

    public TenantId {
        Objects.requireNonNull(id, "tenant id cannot be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("tenant id cannot be blank");
        }
    }

    @Override
    public String toString() {
        return id;
    }
}
