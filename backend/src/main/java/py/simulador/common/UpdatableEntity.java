package py.simulador.common;

import java.time.OffsetDateTime;

/**
 * Interface for entities that track both createdAt and updatedAt.
 */
public interface UpdatableEntity extends AuditableEntity {
    OffsetDateTime getUpdatedAt();
    void setUpdatedAt(OffsetDateTime updatedAt);
}
