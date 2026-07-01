package py.simulador.bot;

import org.springframework.stereotype.Component;
import py.simulador.bot.model.Difficulty;
import py.simulador.bot.model.Personality;
import py.simulador.catalogo.EventoCatalogoEntity;
import py.simulador.catalogo.EventoCatalogoRepository;
import py.simulador.catalogo.ParametroMacroEntity;
import py.simulador.catalogo.ParametroMacroRepository;
import py.simulador.catalogo.ParametroMacroTrimestreEntity;
import py.simulador.catalogo.ParametroMacroTrimestreRepository;
import py.simulador.catalogo.ParametroRubroEntity;
import py.simulador.catalogo.ParametroRubroRepository;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoRepository;
import py.simulador.evento.EventoCompetenciaEntity;
import py.simulador.evento.EventoCompetenciaRepository;
import py.simulador.resultado.RankingTrimestreEntity;
import py.simulador.resultado.RankingTrimestreRepository;
import py.simulador.resultado.ResultadoCalculoEntity;
import py.simulador.resultado.ResultadoCalculoRepository;
import py.simulador.resultado.SnapshotEstadoEntity;
import py.simulador.resultado.SnapshotEstadoRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds a {@link BotContext} for a bot team in a given trimestre.
 *
 * <p>Wires together the same public information a human player would see in
 * the contexto-decision endpoint: own state (caja, inventario, brand, R+D),
 * macroeconomic parameters of the trimestre, active events, competitors with
 * their previous ranking position, and previous-trimestre results.
 *
 * <p>The {@code costoUnitarioEstimado} is derived from the rubro's
 * {@code costo_unit_mp} parameter, which is the dominant unit-cost component.
 * For events that affect costs ({@code COSTO_MP}, {@code COSTO_LOGISTICO})
 * the magnitude is exposed in the event's {@code efectos} map under the key
 * {@code costo_unitario_delta} so the heuristic strategy's price-pass-through
 * logic can react.
 *
 * <p>Defensive: every lookup may return empty; in that case the field stays
 * {@code null}/empty so the heuristic falls back to its conservative default
 * (e.g. {@code esPrimerTrimestre()} returns {@code true} when there are no
 * previous results, and the strategy uses a safer demand estimate).
 */
@Component
public class BotContextBuilder {

    /** Fallback when no rubro parameter is available — kept reasonable for retail. */
    private static final long FALLBACK_COSTO_UNITARIO = 32_000L;

    private final SnapshotEstadoRepository snapshotRepo;
    private final CompetenciaRepository competenciaRepo;
    private final ParametroMacroRepository macroRepo;
    private final ParametroMacroTrimestreRepository macroTrimRepo;
    private final ParametroRubroRepository rubroParamRepo;
    private final EventoCompetenciaRepository eventoCompRepo;
    private final EventoCatalogoRepository eventoCatalogoRepo;
    private final TrimestreRepository trimestreRepo;
    private final ResultadoCalculoRepository resultadoRepo;
    private final RankingTrimestreRepository rankingRepo;
    private final EquipoRepository equipoRepo;

    public BotContextBuilder(SnapshotEstadoRepository snapshotRepo,
                             CompetenciaRepository competenciaRepo,
                             ParametroMacroRepository macroRepo,
                             ParametroMacroTrimestreRepository macroTrimRepo,
                             ParametroRubroRepository rubroParamRepo,
                             EventoCompetenciaRepository eventoCompRepo,
                             EventoCatalogoRepository eventoCatalogoRepo,
                             TrimestreRepository trimestreRepo,
                             ResultadoCalculoRepository resultadoRepo,
                             RankingTrimestreRepository rankingRepo,
                             EquipoRepository equipoRepo) {
        this.snapshotRepo = snapshotRepo;
        this.competenciaRepo = competenciaRepo;
        this.macroRepo = macroRepo;
        this.macroTrimRepo = macroTrimRepo;
        this.rubroParamRepo = rubroParamRepo;
        this.eventoCompRepo = eventoCompRepo;
        this.eventoCatalogoRepo = eventoCatalogoRepo;
        this.trimestreRepo = trimestreRepo;
        this.resultadoRepo = resultadoRepo;
        this.rankingRepo = rankingRepo;
        this.equipoRepo = equipoRepo;
    }

    public BotContext build(EquipoEntity bot, TrimestreEntity trimestre) {
        Difficulty difficulty = parseDifficulty(bot.getDificultad());
        Personality personality = parsePersonality(bot.getPersonalidad());

        // ----------------------------------------------------------------
        // Estado propio: snapshot de INICIO del trimestre actual.
        // ----------------------------------------------------------------
        Optional<SnapshotEstadoEntity> snapOpt = snapshotRepo
                .findByEquipoIdAndTrimestreIdAndMomento(bot.getId(), trimestre.getId(), "INICIO");

        long cajaActual = snapOpt.map(SnapshotEstadoEntity::getCaja).orElse(0L);
        long inventario = snapOpt.map(SnapshotEstadoEntity::getInventario).orElse(0L);
        long idAcumulado = snapOpt.map(SnapshotEstadoEntity::getIdAcumulado).orElse(0L);
        // Capacidad actual del equipo al inicio del trimestre. Si no hay
        // snapshot todavia (e.g. Q1 antes de procesar), el clamp queda
        // deshabilitado via el sentinel CAPACIDAD_DESCONOCIDA.
        long capacidadActual = snapOpt
                .map(SnapshotEstadoEntity::getCapacidad)
                .filter(v -> v > 0)
                .orElse(BotContext.CAPACIDAD_DESCONOCIDA);
        double brandEquity = snapOpt
                .map(SnapshotEstadoEntity::getBrandEquity)
                .map(BigDecimal::doubleValue)
                .orElse(0.0);

        // ----------------------------------------------------------------
        // Competencia + parametros (rubro, macro). Best-effort: si falta,
        // seguimos con valores conservadores.
        // ----------------------------------------------------------------
        CompetenciaEntity comp = competenciaRepo.findById(trimestre.getCompetenciaId()).orElse(null);

        Optional<ParametroRubroEntity> rubroParamOpt = (comp != null && comp.getParametroRubroId() != null)
                ? rubroParamRepo.findById(comp.getParametroRubroId())
                : Optional.empty();

        long costoUnitarioEstimado = rubroParamOpt
                .map(ParametroRubroEntity::getCostoUnitMp)
                .filter(v -> v > 0)
                .orElse(FALLBACK_COSTO_UNITARIO);

        Map<String, Number> parametrosMacro = buildParametrosMacro(comp, trimestre, rubroParamOpt.orElse(null));

        // ----------------------------------------------------------------
        // Eventos activos: incluyen los heredados de Qs anteriores cuya
        // duracion cubre este Q. Cada evento expone tipo_efecto + magnitud
        // y, cuando aplica, costo_unitario_delta para que la heuristica
        // reaccione (passthrough en precio).
        // ----------------------------------------------------------------
        List<BotContext.EventoActivo> eventosActivos = buildEventosActivos(trimestre);

        // ----------------------------------------------------------------
        // Resultados / ranking del trimestre anterior. Si no existe (Q1),
        // todos los campos se quedan en null y esPrimerTrimestre() == true.
        // ----------------------------------------------------------------
        Long ingresoTrimAnt = null;
        Long gananciaTrimAnt = null;
        Integer posicionRankingAnt = null;
        Integer totalEquipos = null;
        Map<Long, Integer> posicionPorEquipoAnt = Map.of();

        if (trimestre.getNumero() > 1) {
            Optional<TrimestreEntity> trimAntOpt = trimestreRepo.findByCompetenciaIdAndNumero(
                    trimestre.getCompetenciaId(), (short) (trimestre.getNumero() - 1));
            if (trimAntOpt.isPresent()) {
                Long trimAntId = trimAntOpt.get().getId();
                Optional<ResultadoCalculoEntity> resOpt = resultadoRepo
                        .findByEquipoIdAndTrimestreId(bot.getId(), trimAntId);
                if (resOpt.isPresent()) {
                    ingresoTrimAnt = resOpt.get().getIngresos();
                    gananciaTrimAnt = resOpt.get().getUtilidadNeta();
                }
                List<RankingTrimestreEntity> rankings = rankingRepo
                        .findByCompetenciaIdAndTrimestreId(trimestre.getCompetenciaId(), trimAntId);
                if (!rankings.isEmpty()) {
                    totalEquipos = rankings.size();
                    posicionPorEquipoAnt = new HashMap<>();
                    for (RankingTrimestreEntity r : rankings) {
                        posicionPorEquipoAnt.put(r.getEquipoId(), (int) r.getPosicion());
                        if (r.getEquipoId().equals(bot.getId())) {
                            posicionRankingAnt = (int) r.getPosicion();
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------------------
        // Competidores: otros equipos de la misma competencia (no incluye
        // self). Solo informacion publica: id, nombre, tipo, posicion en
        // el ultimo ranking (null para Q1 o sin ranking).
        // ----------------------------------------------------------------
        List<BotContext.Competidor> competidores = buildCompetidores(
                bot, trimestre.getCompetenciaId(), posicionPorEquipoAnt);

        return new BotContext(
                bot.getId(),
                trimestre.getId(),
                difficulty,
                personality,
                cajaActual,
                inventario,
                brandEquity,
                idAcumulado,
                costoUnitarioEstimado,
                capacidadActual,
                ingresoTrimAnt,
                gananciaTrimAnt,
                posicionRankingAnt,
                totalEquipos,
                parametrosMacro,
                eventosActivos,
                competidores
        );
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private Map<String, Number> buildParametrosMacro(CompetenciaEntity comp,
                                                     TrimestreEntity trimestre,
                                                     ParametroRubroEntity rubroParam) {
        Map<String, Number> out = new HashMap<>();

        // Rubro-level demand baseline (total trimestre market). Heuristics
        // divide this by the number of teams to get a per-team target.
        if (rubroParam != null && rubroParam.getDemandaBaseTrim() > 0) {
            out.put("demanda_base_trim", rubroParam.getDemandaBaseTrim());
        }

        if (comp == null || comp.getParametroMacroId() == null) {
            return out.isEmpty() ? Map.of() : Map.copyOf(out);
        }
        Optional<ParametroMacroEntity> macroOpt = macroRepo.findById(comp.getParametroMacroId());
        if (macroOpt.isEmpty()) {
            return out.isEmpty() ? Map.of() : Map.copyOf(out);
        }
        ParametroMacroEntity macro = macroOpt.get();

        // Set-level macros (no per-trimestre)
        out.put("salario_minimo_q1", macro.getSalarioMinimoQ1());
        out.put("salario_minimo_q4", macro.getSalarioMinimoQ4());
        if (macro.getIpsPatronal() != null) {
            out.put("ips_patronal", macro.getIpsPatronal());
        }
        if (macro.getIpsTrabajador() != null) {
            out.put("ips_trabajador", macro.getIpsTrabajador());
        }
        if (macro.getTasaIre() != null) {
            out.put("tasa_ire", macro.getTasaIre());
        }
        if (macro.getIvaGeneral() != null) {
            out.put("iva_general", macro.getIvaGeneral());
        }

        // Per-trimestre macros: pick the row matching the current Q (1-based).
        int q = trimestre.getNumero();
        List<ParametroMacroTrimestreEntity> trims = macroTrimRepo.findByMacroId(macro.getId());
        ParametroMacroTrimestreEntity match = trims.stream()
                .filter(t -> t.getTrimestre() == q)
                .findFirst()
                .orElse(null);
        if (match != null) {
            if (match.getInflacionTrim() != null) {
                out.put("inflacion_trim", match.getInflacionTrim());
            }
            if (match.getTipoCambio() != null) {
                out.put("tipo_cambio_usd", match.getTipoCambio());
            }
            if (match.getTpmAnual() != null) {
                out.put("tpm_anual", match.getTpmAnual());
            }
        }
        return out;
    }

    private List<BotContext.EventoActivo> buildEventosActivos(TrimestreEntity trimestre) {
        List<EventoCompetenciaEntity> activos = eventoCompRepo.findActivosParaTrimestre(
                trimestre.getCompetenciaId(), trimestre.getId());
        if (activos.isEmpty()) {
            return List.of();
        }
        List<BotContext.EventoActivo> out = new ArrayList<>(activos.size());
        for (EventoCompetenciaEntity ec : activos) {
            EventoCatalogoEntity cat = eventoCatalogoRepo.findById(ec.getEventoCatalogoId())
                    .orElse(null);
            if (cat == null) continue;

            BigDecimal mag = ec.getMagnitudAplicada() != null
                    ? ec.getMagnitudAplicada()
                    : cat.getMagnitudDefault();
            String tipo = cat.getTipoEfecto();

            Map<String, Number> efectos = new HashMap<>();
            if (mag != null) {
                efectos.put("magnitud", mag);
            }
            if (tipo != null && mag != null) {
                // Generic key for diagnostics.
                efectos.put(tipo.toLowerCase(), mag);
                // Specific key the heuristic strategy reads to apply price passthrough.
                if ("COSTO_MP".equals(tipo) || "COSTO_LOGISTICO".equals(tipo)) {
                    efectos.put("costo_unitario_delta", mag);
                }
            }
            // Override-pesos exposed for context (heuristic does not use them today).
            if (cat.getOverridePesoPrecio() != null) {
                efectos.put("override_peso_precio", cat.getOverridePesoPrecio());
            }
            if (cat.getOverridePesoMarketing() != null) {
                efectos.put("override_peso_marketing", cat.getOverridePesoMarketing());
            }
            if (cat.getOverridePesoCalidad() != null) {
                efectos.put("override_peso_calidad", cat.getOverridePesoCalidad());
            }
            if (cat.getOverridePesoMarca() != null) {
                efectos.put("override_peso_marca", cat.getOverridePesoMarca());
            }

            out.add(new BotContext.EventoActivo(
                    cat.getCodigo(),
                    cat.getDescripcion(),
                    Map.copyOf(efectos)
            ));
        }
        return out;
    }

    private List<BotContext.Competidor> buildCompetidores(EquipoEntity self,
                                                          Long competenciaId,
                                                          Map<Long, Integer> posicionPorEquipoAnt) {
        List<EquipoEntity> equipos = equipoRepo.findByCompetenciaId(competenciaId);
        if (equipos.isEmpty()) {
            return List.of();
        }
        List<BotContext.Competidor> out = new ArrayList<>(equipos.size() - 1);
        for (EquipoEntity e : equipos) {
            if (e.getId().equals(self.getId())) continue;
            Integer pos = posicionPorEquipoAnt.get(e.getId());
            String tipo = e.getTipo() != null ? e.getTipo() : "HUMANO";
            out.add(new BotContext.Competidor(e.getId(), e.getNombreEmpresa(), tipo, pos));
        }
        return out;
    }

    private static Difficulty parseDifficulty(String raw) {
        if (raw == null || raw.isBlank()) {
            return Difficulty.MEDIO; // sane default
        }
        try {
            return Difficulty.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return Difficulty.MEDIO;
        }
    }

    private static Personality parsePersonality(String raw) {
        if (raw == null || raw.isBlank()) {
            return Personality.BALANCEADO;
        }
        try {
            return Personality.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return Personality.BALANCEADO;
        }
    }
}
