package py.simulador.config;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link JdbcConversions} converters.
 *
 * <p>These tests do not require a database — they exercise the converter
 * classes directly to prove the contract:
 * <ul>
 *   <li>writing wraps a {@link JsonbValue} into a {@link PGobject} typed as {@code jsonb};</li>
 *   <li>reading unwraps a {@link PGobject} into a {@link JsonbValue} (or {@code null}
 *       when the underlying value is {@code null});</li>
 *   <li>{@link JsonbValue#of(String)} returns {@code null} for {@code null} input.</li>
 * </ul>
 *
 * <p>An end-to-end roundtrip through PostgreSQL is intentionally NOT included here —
 * the existing {@link py.simulador.integration.CompetenciaConBotsE2ETest} already
 * persists and reloads {@link py.simulador.equipo.EquipoEntity} via Testcontainers,
 * so any conversion failure on the {@code bot_config} column would surface there.
 */
class JdbcConversionsTest {

    @Test
    void writingConverter_wrapsJsonInPGobjectWithJsonbType() throws SQLException {
        var converter = new JdbcConversions.JsonbValueToPGobjectWritingConverter();

        PGobject result = converter.convert(new JsonbValue("{\"k\":\"v\"}"));

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("jsonb");
        assertThat(result.getValue()).isEqualTo("{\"k\":\"v\"}");
    }

    @Test
    void writingConverter_acceptsNullInnerJson() throws SQLException {
        var converter = new JdbcConversions.JsonbValueToPGobjectWritingConverter();

        PGobject result = converter.convert(new JsonbValue(null));

        assertThat(result).isNotNull();
        assertThat(result.getType()).isEqualTo("jsonb");
        assertThat(result.getValue()).isNull();
    }

    @Test
    void readingConverter_unwrapsPGobjectIntoJsonbValue() throws SQLException {
        var pg = new PGobject();
        pg.setType("jsonb");
        pg.setValue("{\"k\":\"v\"}");
        var converter = new JdbcConversions.PGobjectToJsonbValueReadingConverter();

        JsonbValue result = converter.convert(pg);

        assertThat(result).isNotNull();
        assertThat(result.json()).isEqualTo("{\"k\":\"v\"}");
    }

    @Test
    void readingConverter_handlesNullValue() throws SQLException {
        var pg = new PGobject();
        pg.setType("jsonb");
        pg.setValue(null);
        var converter = new JdbcConversions.PGobjectToJsonbValueReadingConverter();

        assertThat(converter.convert(pg)).isNull();
    }

    @Test
    void jsonbValueOf_returnsNullForNullInput() {
        assertThat(JsonbValue.of(null)).isNull();
    }

    @Test
    void jsonbValueOf_wrapsNonNullInput() {
        JsonbValue v = JsonbValue.of("{\"a\":1}");
        assertThat(v).isNotNull();
        assertThat(v.json()).isEqualTo("{\"a\":1}");
    }
}
