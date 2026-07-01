package py.simulador.catalogo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Materia prima (item del Bill of Materials) de un rubro. Informacion narrativa:
 * la suma de {@code costoUnitario} de todas las materias primas de un rubro debe
 * igualar {@code parametro_rubro.costo_unit_mp}. NO afecta al motor de simulacion.
 */
@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "materia_prima_rubro")
public class MateriaPrimaRubroEntity {

    @Id
    private Long id;
    private Long rubroId;
    private String nombre;
    private long costoUnitario;
    private int orden;
}
