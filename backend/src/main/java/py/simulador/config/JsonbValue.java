package py.simulador.config;

/**
 * Wrapper type for fields that map to PostgreSQL jsonb columns.
 * Spring Data JDBC's default String-to-varchar mapping rejects jsonb
 * columns, so jsonb fields must use this wrapper to trigger the
 * targeted converters registered in {@link JdbcConversions}.
 *
 * <p>Usage in entity: {@code private JsonbValue botConfig;}
 * <p>Construction: {@code new JsonbValue("{\"key\":\"value\"}")} or pass {@code null}.
 */
public record JsonbValue(String json) {
    public static JsonbValue of(String json) {
        return json == null ? null : new JsonbValue(json);
    }
}
