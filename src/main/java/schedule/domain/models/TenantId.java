package schedule.domain.models;

public record TenantId(String id) {

    public TenantId {
        if (id == null) {
            throw new IllegalArgumentException("tenant id cannot be null");
        }
    }
}
