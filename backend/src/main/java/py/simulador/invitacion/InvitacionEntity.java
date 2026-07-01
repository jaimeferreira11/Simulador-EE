package py.simulador.invitacion;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import py.simulador.common.AuditableEntity;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "invitacion")
public class InvitacionEntity implements AuditableEntity {

    @Id
    private Long id;
    private Long equipoId;
    private String email;
    private String nombreCompleto;
    private String token;
    private String estado;
    private Long areaId;
    private boolean esCapitan;
    private Long creadaPor;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime acceptedAt;
}
