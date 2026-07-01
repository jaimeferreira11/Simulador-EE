package py.simulador.bot;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.bot.log.BotDecisionLogEntity;
import py.simulador.bot.log.BotDecisionLogRepository;
import py.simulador.bot.model.Difficulty;
import py.simulador.bot.model.Personality;
import py.simulador.bot.strategy.LlmBotStrategy;
import py.simulador.common.BusinessValidationException;
import py.simulador.common.InvalidStateTransitionException;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.decision.DecisionEquipoEntity;
import py.simulador.decision.DecisionService;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;
import py.simulador.trimestre.TrimestreStateMachine;
import py.simulador.usuario.UsuarioRepository;

import java.util.List;

/**
 * Orquestador de bots: cuando un trimestre se abre para decisiones,
 * este servicio genera y persiste decisiones para todos los equipos
 * tipo BOT de la competencia.
 *
 * <p><b>Aislamiento de errores:</b> si un bot falla (ej. context builder
 * lanza, strategy lanza, persistencia falla), se loguea y se continúa con
 * los siguientes. Nunca se aborta la apertura del trimestre por un bot.
 *
 * <p><b>Async dispatch (Fase 2):</b> los bots EXPERTO (LLM) se despachan a
 * un executor — la apertura del trimestre no espera a que el LLM responda.
 * Los bots heurísticos (FACIL/MEDIO/DIFICIL) siguen siendo síncronos.
 *
 * <p><b>Auditoría (Fase 2):</b> cada generación EXPERTO escribe una fila en
 * {@code sim.bot_decision_log} con latencia, tokens y motivo de fallback. Los
 * heurísticos no escriben filas — basta con logs de aplicación.
 *
 * <p>El usuario "system-bot" (email {@code system-bot@simulador.local}) se
 * usa como autor de todas las decisiones generadas. Está seeded por
 * Flyway (V202605121201).
 */
@Service
public class BotDecisionService {

    private static final Logger log = LoggerFactory.getLogger(BotDecisionService.class);
    private static final String SYSTEM_BOT_EMAIL = "system-bot@simulador.local";

    private final EquipoRepository equipoRepo;
    private final TrimestreRepository trimestreRepo;
    private final BotStrategyFactory factory;
    private final BotContextBuilder contextBuilder;
    private final DecisionService decisionService;
    private final UsuarioRepository usuarioRepo;
    private final BotDecisionLogRepository logRepo;

    private Long systemBotUserId;

    public BotDecisionService(EquipoRepository equipoRepo,
                              TrimestreRepository trimestreRepo,
                              BotStrategyFactory factory,
                              BotContextBuilder contextBuilder,
                              DecisionService decisionService,
                              UsuarioRepository usuarioRepo,
                              BotDecisionLogRepository logRepo) {
        this.equipoRepo = equipoRepo;
        this.trimestreRepo = trimestreRepo;
        this.factory = factory;
        this.contextBuilder = contextBuilder;
        this.decisionService = decisionService;
        this.usuarioRepo = usuarioRepo;
        this.logRepo = logRepo;
    }

    @PostConstruct
    void init() {
        this.systemBotUserId = usuarioRepo.findByEmail(SYSTEM_BOT_EMAIL)
                .orElseThrow(() -> new IllegalStateException(
                        "System bot user not seeded: " + SYSTEM_BOT_EMAIL))
                .getId();
        log.info("BotDecisionService inicializado. systemBotUserId={}", systemBotUserId);
    }

    /**
     * Genera decisiones para todos los bots del trimestre dado.
     * Tolerante a fallos: si un bot falla, sigue con los demás.
     *
     * <p>Heurísticos (FACIL/MEDIO/DIFICIL) corren síncronos dentro de la
     * transacción de apertura. Los EXPERTO se despachan a un executor para
     * no bloquear la apertura mientras el LLM responde.
     *
     * @param trimestreId id del trimestre recién abierto (ABIERTO_DECISIONES)
     */
    @Transactional
    public void generarDecisionesParaTrimestreAbierto(Long trimestreId) {
        TrimestreEntity trimestre = trimestreRepo.findById(trimestreId)
                .orElseThrow(() -> new IllegalStateException(
                        "Trimestre no existe: " + trimestreId));

        List<EquipoEntity> bots = equipoRepo
                .findByCompetenciaIdAndTipo(trimestre.getCompetenciaId(), "BOT");

        if (bots.isEmpty()) {
            log.debug("No hay bots en competencia {} para trimestre {}",
                    trimestre.getCompetenciaId(), trimestreId);
            return;
        }

        log.info("Generando decisiones para {} bot(s) en trimestre {} (competencia {})",
                bots.size(), trimestreId, trimestre.getCompetenciaId());

        int sync = 0, async = 0, fail = 0;
        for (EquipoEntity bot : bots) {
            Difficulty d;
            try {
                d = Difficulty.valueOf(bot.getDificultad());
            } catch (Exception e) {
                log.error("Bot {} con dificultad inválida '{}': {}",
                        bot.getId(), bot.getDificultad(), e.getMessage());
                fail++;
                continue;
            }

            if (d == Difficulty.EXPERTO) {
                // Async — no bloquea la apertura del trimestre con la latencia del LLM.
                generarDecisionExpertoAsync(bot.getId(), trimestreId);
                async++;
            } else {
                try {
                    generarDecisionSync(bot, trimestre);
                    sync++;
                } catch (Exception e) {
                    log.error("Error generando decisión para bot {} en trimestre {}: {}",
                            bot.getId(), trimestreId, e.getMessage(), e);
                    fail++;
                }
            }
        }

        log.info("Generación de decisiones de bots finalizada para trimestre {}: sync={} async={} fail={}",
                trimestreId, sync, async, fail);
    }

    /**
     * Generación síncrona para bots heurísticos (FACIL/MEDIO/DIFICIL).
     * Lanza si algo sale mal — el caller decide si propagar o aislar.
     */
    private void generarDecisionSync(EquipoEntity bot, TrimestreEntity trimestre) {
        BotContext ctx = contextBuilder.build(bot, trimestre);
        BotStrategy strategy = factory.forDifficulty(
                ctx.difficulty(), ctx.personality());
        BotDecisionDTO decision = strategy.generate(ctx);
        decisionService.upsertDecisionBot(
                bot.getId(), trimestre.getId(), decision, systemBotUserId);
        log.info("Bot {} ({}) generó decisión para trimestre {}: precio={} prod={}",
                bot.getId(), bot.getNombreEmpresa(), trimestre.getId(),
                decision.precioUnitario(), decision.produccionUnidades());
    }

    /**
     * Despacho async para bots EXPERTO (LLM). Se ejecuta fuera de la transacción
     * de apertura — cada llamada construye su propia transacción al persistir
     * la decisión.
     *
     * <p>Visibility: package-private para que tests unitarios puedan invocarlo
     * directamente sin spinning del executor de Spring.
     *
     * <p>Nota: se vuelve a buscar la entidad equipo/trimestre dentro del método
     * porque el async se ejecuta en otro hilo y no puede confiar en entidades
     * traídas en la transacción que ya se commiteó.
     */
    @Async("botExecutor")
    void generarDecisionExpertoAsync(Long equipoId, Long trimestreId) {
        EquipoEntity bot = equipoRepo.findById(equipoId).orElse(null);
        TrimestreEntity tri = trimestreRepo.findById(trimestreId).orElse(null);
        if (bot == null || tri == null) {
            log.warn("Bot async: equipo {} o trimestre {} ya no existe — abortando",
                    equipoId, trimestreId);
            return;
        }
        try {
            generarDecisionExpertoYRegistrar(bot, tri);
        } catch (Exception e) {
            log.error("Error en bot EXPERTO {} trimestre {}: {}",
                    equipoId, trimestreId, e.getMessage(), e);
        }
    }

    /**
     * Genera la decisión para un bot EXPERTO + escribe la fila en
     * {@code bot_decision_log}. Llamado por el dispatch async y por
     * {@link #regenerarDecisionDeBot} (síncrono).
     *
     * @return la decisión persistida (estado ENVIADA)
     */
    private DecisionEquipoEntity generarDecisionExpertoYRegistrar(EquipoEntity bot, TrimestreEntity tri) {
        BotContext ctx = contextBuilder.build(bot, tri);
        BotStrategy strategy = factory.forDifficulty(
                ctx.difficulty(), ctx.personality());

        long t0 = System.currentTimeMillis();
        BotDecisionDTO decision;
        try {
            decision = strategy.generate(ctx);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - t0;
            // Esto no debería pasar (LlmBotStrategy ya capturó), pero por defensa:
            persistLog(bot.getId(), tri.getId(),
                    LlmBotStrategy.Outcome.LLM_FALLBACK.name(),
                    (int) elapsed, null, null,
                    "Strategy threw unhandled: " + e.getMessage());
            throw e;
        }
        long elapsed = System.currentTimeMillis() - t0;

        DecisionEquipoEntity persisted = decisionService.upsertDecisionBot(
                bot.getId(), tri.getId(), decision, systemBotUserId);

        // Audit row — solo si la strategy es realmente una LlmBotStrategy.
        // (Si la factory degradó EXPERTO a heurístico DIFICIL por falta de
        // pipeline LLM, conviene registrarlo igual como LLM_FALLBACK.)
        String outcomeName;
        Integer pTok = null, cTok = null;
        String reason = null;
        if (strategy instanceof LlmBotStrategy llm && llm.lastResult() != null) {
            outcomeName = llm.lastResult().outcome().name();
            pTok = llm.lastResult().promptTokens();
            cTok = llm.lastResult().completionTokens();
            reason = llm.lastResult().fallbackReason();
        } else {
            outcomeName = LlmBotStrategy.Outcome.LLM_FALLBACK.name();
            reason = "LLM pipeline unavailable — degraded to heuristic at factory";
        }
        persistLog(bot.getId(), tri.getId(), outcomeName,
                (int) elapsed, pTok, cTok, reason);

        log.info("Bot EXPERTO {} ({}) trimestre {}: outcome={} latency={}ms tokens={}/{}",
                bot.getId(), bot.getNombreEmpresa(), tri.getId(),
                outcomeName, elapsed, pTok, cTok);
        return persisted;
    }

    private void persistLog(Long equipoId, Long trimestreId, String strategyUsed,
                            int latencyMs, Integer pTok, Integer cTok, String reason) {
        try {
            BotDecisionLogEntity row = new BotDecisionLogEntity();
            row.setEquipoId(equipoId);
            row.setTrimestreId(trimestreId);
            row.setStrategyUsed(strategyUsed);
            row.setLatencyMs(latencyMs);
            row.setPromptTokens(pTok);
            row.setCompletionTokens(cTok);
            row.setFallbackReason(reason);
            logRepo.save(row);
        } catch (Exception e) {
            log.warn("No se pudo registrar bot_decision_log para equipo {} tri {}: {}",
                    equipoId, trimestreId, e.getMessage());
        }
    }

    /**
     * Re-ejecuta la heurística del bot y sobrescribe la decisión actual del
     * equipo para el trimestre dado. Endpoint usado por el moderador (por ej.
     * después de calibrar coeficientes) para "re-tirar" la decisión sin
     * tener que reabrir el trimestre completo.
     *
     * <p>A diferencia de {@link #generarDecisionesParaTrimestreAbierto(Long)}
     * (que es tolerante a fallos y trabaja sobre todos los bots de la
     * competencia), esta operación es puntual: si algo falla, propaga la
     * excepción para que el caller (controller) la traduzca al status HTTP
     * adecuado.
     *
     * <p>Para bots EXPERTO la regeneración corre sincrónicamente (el moderador
     * espera el resultado) y también escribe la fila de auditoría correspondiente.
     *
     * @param equipoId      id del equipo (debe ser {@code tipo='BOT'})
     * @param trimestreId   id del trimestre (debe estar en
     *                      {@link TrimestreStateMachine#ABIERTO_DECISIONES})
     * @param callerUserId  id del usuario que dispara la operación (sólo para
     *                      log; la verificación de rol/permiso se hace en la
     *                      capa de seguridad/controller)
     * @return decisión persistida (estado {@code ENVIADA})
     * @throws ResourceNotFoundException        si el equipo o trimestre no existen
     * @throws BusinessValidationException      si el equipo no es BOT (mapea a 422)
     * @throws InvalidStateTransitionException  si el trimestre no está
     *                                          ABIERTO_DECISIONES (mapea a 409)
     */
    @Transactional
    public DecisionEquipoEntity regenerarDecisionDeBot(Long equipoId, Long trimestreId, Long callerUserId) {
        EquipoEntity equipo = equipoRepo.findById(equipoId)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo", equipoId));
        if (!equipo.esBot()) {
            throw new BusinessValidationException(
                    "Solo equipos bot pueden regenerar decisiones (equipo " + equipoId
                            + " es " + equipo.getTipo() + ")");
        }

        TrimestreEntity trimestre = trimestreRepo.findById(trimestreId)
                .orElseThrow(() -> new ResourceNotFoundException("Trimestre", trimestreId));
        if (!TrimestreStateMachine.ABIERTO_DECISIONES.equals(trimestre.getEstado())) {
            throw new InvalidStateTransitionException("trimestre", trimestre.getEstado(),
                    "regenerar decisión de bot",
                    TrimestreStateMachine.transicionesValidas(trimestre.getEstado()));
        }

        // Authorization (caller must be moderador de la competencia) is enforced
        // upstream by Spring Security on the controller. We only log here.

        Difficulty d = Difficulty.valueOf(equipo.getDificultad());
        if (d == Difficulty.EXPERTO) {
            // Sincrónico desde el endpoint — el moderador espera el resultado.
            DecisionEquipoEntity persisted = generarDecisionExpertoYRegistrar(equipo, trimestre);
            log.info("Bot EXPERTO {} regenerated decision for trimestre {} by user {}",
                    equipoId, trimestreId, callerUserId);
            return persisted;
        }

        BotContext ctx = contextBuilder.build(equipo, trimestre);
        BotStrategy strategy = factory.forDifficulty(
                d, Personality.valueOf(equipo.getPersonalidad()));
        BotDecisionDTO decision = strategy.generate(ctx);
        DecisionEquipoEntity persisted = decisionService.upsertDecisionBot(
                equipoId, trimestreId, decision, systemBotUserId);

        log.info("Bot {} regenerated decision for trimestre {} by user {}: precio={} prod={}",
                equipoId, trimestreId, callerUserId,
                decision.precioUnitario(), decision.produccionUnidades());

        return persisted;
    }
}
