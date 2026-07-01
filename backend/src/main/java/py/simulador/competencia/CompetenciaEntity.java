package py.simulador.competencia;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import py.simulador.common.UpdatableEntity;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "competencia")
public class CompetenciaEntity implements UpdatableEntity {

    @Id
    private Long id;
    private String codigo;
    private String nombre;
    private Long rubroId;
    private Long parametroMacroId;
    private Long parametroRubroId;
    private Long moderadorId;
    private Long entidadId;
    private Long escenarioId;

    private short numTrimestres;
    private short numEquiposMax;
    /** Máximo de integrantes por equipo (miembros + invitaciones pendientes). NULL = sin límite. */
    private Short maxIntegrantesEquipo;

    private long cajaInicial;
    private long capacidadInicial;
    private short headcountInicial;
    private long salarioInicial;
    private long inventarioInicial;
    private long valorPlantaInicial;

    private boolean bancarrotaHabilitada;
    private boolean iaHabilitada;

    private String estado;

    private OffsetDateTime inicioAt;
    private OffsetDateTime cierreAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
