package py.simulador.bot;

import py.simulador.bot.model.Difficulty;
import py.simulador.bot.model.Personality;

import java.util.List;
import java.util.Map;

/**
 * Snapshot inmutable de lo que ve un bot al decidir.
 * Contiene EXACTAMENTE la información pública que tendría un jugador humano.
 * NUNCA incluye decisiones de otros equipos.
 */
public record BotContext(
    Long equipoId,
    Long trimestreId,
    Difficulty difficulty,
    Personality personality,

    // Estado propio del equipo
    long cajaActual,                    // guaraníes
    long inventarioUnidades,
    double brandEquity,                 // 0.0 - 1.0
    long rdAcumulado,                   // guaraníes
    long costoUnitarioEstimado,         // guaraníes
    long capacidadActual,               // unidades / trimestre (techo de produccion)

    // Resultado del trimestre anterior (null si es Q1)
    Long ingresoTrimestreAnterior,
    Long gananciaTrimestreAnterior,
    Integer posicionRankingAnterior,
    Integer totalEquipos,

    // Contexto macroeconómico
    Map<String, Number> parametrosMacro,

    // Eventos activos (con sus efectos publicados)
    List<EventoActivo> eventosActivos,

    // Competidores (nombres y tipos, SIN decisiones)
    List<Competidor> competidores
) {
    /** Sentinel for tests/contexts where capacity is unknown — disables the clamp. */
    public static final long CAPACIDAD_DESCONOCIDA = Long.MAX_VALUE;

    public record EventoActivo(
        String codigo,
        String descripcion,
        Map<String, Number> efectos
    ) {}

    public record Competidor(
        Long equipoId,
        String nombre,
        String tipo,  // "HUMANO" | "BOT"
        Integer posicionAnterior
    ) {}

    public boolean esPrimerTrimestre() {
        return ingresoTrimestreAnterior == null;
    }
}
