package py.simulador.config;

import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.CustomConversions;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.mapping.JdbcSimpleTypes;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.relational.core.dialect.Dialect;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Type-safe converters for fields wrapped in {@link JsonbValue}.
 * Only affects entity fields explicitly typed as {@code JsonbValue} —
 * normal {@code String} fields keep the default varchar mapping.
 *
 * <p>Background: a naive {@code String <-> PGobject} converter would be
 * applied globally to every {@code String} column in the schema, which
 * breaks the standard varchar driver path. Wrapping jsonb fields in a
 * dedicated type isolates the converter to only those fields.
 *
 * <p>Implementation note: this overrides Spring Data JDBC's default
 * {@code jdbcCustomConversions} bean. We must therefore preserve the
 * dialect-specific store converters (Postgres provides, among others,
 * the {@code java.sql.Timestamp <-> java.time.OffsetDateTime} pair that
 * {@code AuditableEntity#createdAt} relies on). The bean is built
 * mirroring {@code AbstractJdbcConfiguration#jdbcCustomConversions}.
 */
@Configuration
@Profile("!test-h2")
public class JdbcConversions {

    @Bean
    JdbcCustomConversions jdbcCustomConversions(Dialect dialect) {
        SimpleTypeHolder simpleTypeHolder = dialect.simpleTypes().isEmpty()
                ? JdbcSimpleTypes.HOLDER
                : new SimpleTypeHolder(dialect.simpleTypes(), JdbcSimpleTypes.HOLDER);

        List<Object> storeConverters = new ArrayList<>();
        storeConverters.addAll(dialect.getConverters());
        storeConverters.addAll(JdbcCustomConversions.storeConverters());

        List<Object> userConverters = List.of(
                new JsonbValueToPGobjectWritingConverter(),
                new PGobjectToJsonbValueReadingConverter()
        );

        return new JdbcCustomConversions(
                CustomConversions.StoreConversions.of(simpleTypeHolder, storeConverters),
                userConverters
        );
    }

    @WritingConverter
    static class JsonbValueToPGobjectWritingConverter implements Converter<JsonbValue, PGobject> {
        @Override
        public PGobject convert(JsonbValue source) {
            PGobject obj = new PGobject();
            obj.setType("jsonb");
            try {
                obj.setValue(source.json());
            } catch (SQLException e) {
                throw new IllegalArgumentException("Failed to set jsonb value", e);
            }
            return obj;
        }
    }

    @ReadingConverter
    static class PGobjectToJsonbValueReadingConverter implements Converter<PGobject, JsonbValue> {
        @Override
        public JsonbValue convert(PGobject source) {
            return source.getValue() == null ? null : new JsonbValue(source.getValue());
        }
    }
}
