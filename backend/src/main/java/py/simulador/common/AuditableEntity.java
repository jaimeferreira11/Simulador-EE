package py.simulador.common;

import java.time.OffsetDateTime;

/**
 * Interface for entities with createdAt timestamp.
 * Extend with UpdatableEntity for entities that also track updatedAt.
 */
public interface AuditableEntity {
    OffsetDateTime getCreatedAt();
    void setCreatedAt(OffsetDateTime createdAt);
}
