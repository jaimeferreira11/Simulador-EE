package py.simulador.invitacion.importacion;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Resumen del resultado de una invitación masiva por CSV a un equipo.
 *
 * <p>Forma JSON (snake_case, alineada con {@code ImportResultDto} de la carga de usuarios):
 * <pre>
 * {
 *   "total": 3,
 *   "invitados": [ { "fila": 1, "email": "..." } ],
 *   "errores":   [ { "fila": 2, "email": "...", "motivo": "..." } ]
 * }
 * </pre>
 *
 * @param total     cantidad de filas de datos procesadas (sin contar la cabecera)
 * @param invitados filas invitadas exitosamente (incluye invitaciones pendientes ya existentes,
 *                  que {@code invitar(...)} trata como idempotentes)
 * @param errores   filas que fallaron (reportadas, no fatales)
 */
public record InvitacionImportResultDto(
        @JsonProperty("total") int total,
        @JsonProperty("invitados") List<Invitado> invitados,
        @JsonProperty("errores") List<ErrorFila> errores
) {

    /** Una fila invitada con éxito. */
    public record Invitado(
            @JsonProperty("fila") int fila,
            @JsonProperty("email") String email
    ) {}

    /** Una fila que falló, con el motivo legible del fallo. */
    public record ErrorFila(
            @JsonProperty("fila") int fila,
            @JsonProperty("email") String email,
            @JsonProperty("motivo") String motivo
    ) {}
}
