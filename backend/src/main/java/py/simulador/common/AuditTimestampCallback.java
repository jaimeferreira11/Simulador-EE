package py.simulador.common;

import org.springframework.data.relational.core.mapping.event.BeforeSaveCallback;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * Spring Data JDBC callback that sets createdAt/updatedAt automatically
 * before every save, equivalent to JPA's @PrePersist / @PreUpdate.
 */
@Component
public class AuditTimestampCallback implements BeforeSaveCallback<Object> {

    @Override
    public Object onBeforeSave(Object entity, org.springframework.data.relational.core.conversion.MutableAggregateChange<Object> change) {
        if (entity instanceof AuditableEntity auditable) {
            if (auditable.getCreatedAt() == null) {
                auditable.setCreatedAt(OffsetDateTime.now());
            }
        }
        if (entity instanceof UpdatableEntity updatable) {
            updatable.setUpdatedAt(OffsetDateTime.now());
        }
        return entity;
    }
}
