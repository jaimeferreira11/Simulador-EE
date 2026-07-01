package py.simulador.usuario.importacion;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Resumen del resultado de una importación masiva de usuarios vía CSV.
 *
 * <p>Forma JSON (snake_case, alineada con la convención de DTOs generados):
 * <pre>
 * {
 *   "total": 3,
 *   "creados": [ { "fila": 1, "email": "...", "usuario_id": 10 } ],
 *   "errores": [ { "fila": 2, "email": "...", "motivo": "..." } ]
 * }
 * </pre>
 *
 * @param total   cantidad de filas de datos procesadas (sin contar la cabecera)
 * @param creados filas creadas exitosamente
 * @param errores filas que fallaron (reportadas, no fatales)
 */
public record ImportResultDto(
        @JsonProperty("total") int total,
        @JsonProperty("creados") List<Creado> creados,
        @JsonProperty("errores") List<ErrorFila> errores
) {

    /** Una fila creada con éxito. */
    public record Creado(
            @JsonProperty("fila") int fila,
            @JsonProperty("email") String email,
            @JsonProperty("usuario_id") Long usuarioId
    ) {}

    /** Una fila que falló, con el motivo legible del fallo. */
    public record ErrorFila(
            @JsonProperty("fila") int fila,
            @JsonProperty("email") String email,
            @JsonProperty("motivo") String motivo
    ) {}
}
