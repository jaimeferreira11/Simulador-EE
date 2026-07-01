package py.simulador.config;

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

import java.util.ArrayList;
import java.util.List;

/**
 * H2-only replacement for {@link JdbcConversions}, active under the {@code test-h2}
 * profile. Stores {@link JsonbValue} as plain VARCHAR/CLOB instead of PostgreSQL
 * {@code jsonb} (H2 has no native jsonb type and {@code org.postgresql.util.PGobject}
 * is not available on H2 connections).
 *
 * <p>Limitations: tests using this profile cannot exercise jsonb-specific operators
 * ({@code ->}, {@code ->>}, {@code @>}, {@code jsonb_build_object}, etc.). Such
 * tests must use {@code IntegrationTestBase} (Testcontainers + Postgres).
 */
@Configuration
@Profile("test-h2")
public class H2JdbcConversions {

    @Bean
    JdbcCustomConversions jdbcCustomConversions(Dialect dialect) {
        SimpleTypeHolder simpleTypeHolder = dialect.simpleTypes().isEmpty()
                ? JdbcSimpleTypes.HOLDER
                : new SimpleTypeHolder(dialect.simpleTypes(), JdbcSimpleTypes.HOLDER);

        List<Object> storeConverters = new ArrayList<>();
        storeConverters.addAll(dialect.getConverters());
        storeConverters.addAll(JdbcCustomConversions.storeConverters());

        List<Object> userConverters = List.of(
                new JsonbValueToStringWritingConverter(),
                new StringToJsonbValueReadingConverter()
        );

        return new JdbcCustomConversions(
                CustomConversions.StoreConversions.of(simpleTypeHolder, storeConverters),
                userConverters
        );
    }

    @WritingConverter
    static class JsonbValueToStringWritingConverter implements Converter<JsonbValue, String> {
        @Override
        public String convert(JsonbValue source) {
            return source.json();
        }
    }

    @ReadingConverter
    static class StringToJsonbValueReadingConverter implements Converter<String, JsonbValue> {
        @Override
        public JsonbValue convert(String source) {
            return source == null ? null : new JsonbValue(source);
        }
    }
}
