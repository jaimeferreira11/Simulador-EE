package py.simulador.catalogo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import py.simulador.IntegrationTestBase;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Invariante critica del BOM (materia_prima_rubro):
 *
 * <p>Para todo rubro que tenga filas de materia_prima_rubro, la suma de
 * {@code costo_unitario} debe ser EXACTAMENTE igual al
 * {@code parametro_rubro.costo_unit_mp} de su parametro de rubro activo.
 *
 * <p>El BOM es informacion narrativa que se apoya sobre un numero (el costo
 * unitario de materia prima) que el motor de simulacion y el Golden File ya
 * usan. Si esta invariante se rompe, el desglose contado al jugador dejaria de
 * cuadrar con el costo real que aplica el motor.
 *
 * <p>Usa PostgreSQL real (Testcontainers via {@link IntegrationTestBase}) con
 * el seed aplicado por Flyway (V202604211007 + V202606161100).
 */
class MateriaPrimaRubroInvariantTest extends IntegrationTestBase {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("SUM(materia_prima_rubro.costo_unitario) == parametro_rubro.costo_unit_mp para cada rubro con BOM")
    void bomSumEqualsBaseCostoUnitMp() {
        // Solo rubros que tienen BOM definido. Para cada uno comparamos la suma
        // del BOM contra el costo_unit_mp del parametro de rubro activo.
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.codigo                  AS codigo,
                       SUM(mpr.costo_unitario)   AS bom_total,
                       pr.costo_unit_mp          AS base_costo
                  FROM sim.materia_prima_rubro mpr
                  JOIN sim.rubro r            ON r.id = mpr.rubro_id
                  JOIN sim.parametro_rubro pr ON pr.rubro_id = r.id AND pr.activo = TRUE
                 GROUP BY r.codigo, pr.costo_unit_mp
                """);

        // Debe existir al menos el BOM de RETAIL_CONV.
        assertThat(rows)
                .as("Debe haber al menos un rubro con BOM (RETAIL_CONV)")
                .isNotEmpty()
                .anySatisfy(row -> assertThat(row.get("codigo")).isEqualTo("RETAIL_CONV"));

        for (Map<String, Object> row : rows) {
            String codigo = (String) row.get("codigo");
            long bomTotal = ((Number) row.get("bom_total")).longValue();
            long baseCosto = ((Number) row.get("base_costo")).longValue();

            assertThat(bomTotal)
                    .as("La suma del BOM de '%s' (%d) debe igualar costo_unit_mp (%d)",
                            codigo, bomTotal, baseCosto)
                    .isEqualTo(baseCosto);
        }
    }
}
