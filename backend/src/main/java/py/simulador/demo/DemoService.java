package py.simulador.demo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.api.generated.model.DecisionInput;
import py.simulador.common.BusinessValidationException;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.competencia.CompetenciaService;
import py.simulador.decision.DecisionEquipoEntity;
import py.simulador.decision.DecisionService;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;
import py.simulador.trimestre.TrimestreService;
import py.simulador.trimestre.TrimestreStateMachine;
import py.simulador.usuario.UsuarioEntity;
import py.simulador.usuario.UsuarioRepository;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates demo-specific operations for the DEMO competencia.
 *
 * <p>The DEMO competencia is a pre-configured, always-on competition used for
 * stakeholder presentations. It has one HUMANO team whose sole member is the
 * synthetic CEO user ({@value DemoConstants#CEO_EMAIL}), plus three BOT teams.
 *
 * <p>Methods in this service bypass the normal JWT-based authorization checks
 * that guard player endpoints; they are only valid for the DEMO competencia.
 */
@Service
public class DemoService {

    private final CompetenciaRepository competenciaRepo;
    private final CompetenciaService competenciaService;
    private final EquipoRepository equipoRepo;
    private final TrimestreRepository trimestreRepo;
    private final UsuarioRepository usuarioRepo;
    private final DecisionService decisionService;
    private final TrimestreService trimestreService;
    private final JdbcTemplate jdbc;

    public DemoService(CompetenciaRepository competenciaRepo,
                       CompetenciaService competenciaService,
                       EquipoRepository equipoRepo,
                       TrimestreRepository trimestreRepo,
                       UsuarioRepository usuarioRepo,
                       DecisionService decisionService,
                       TrimestreService trimestreService,
                       JdbcTemplate jdbc) {
        this.competenciaRepo = competenciaRepo;
        this.competenciaService = competenciaService;
        this.equipoRepo = equipoRepo;
        this.trimestreRepo = trimestreRepo;
        this.usuarioRepo = usuarioRepo;
        this.decisionService = decisionService;
        this.trimestreService = trimestreService;
        this.jdbc = jdbc;
    }

    // -----------------------------------------------------------------------
    // Guard
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} iff the competencia has the reserved DEMO code.
     */
    public boolean esDemo(CompetenciaEntity c) {
        return DemoConstants.COMPETENCIA_CODIGO.equals(c.getCodigo());
    }

    // -----------------------------------------------------------------------
    // Decision CEO
    // -----------------------------------------------------------------------

    /**
     * Persists a player decision on behalf of the synthetic CEO user.
     *
     * <p>The payload is a loosely-typed map accepted from demo endpoints (e.g.
     * a REST controller or an internal bootstrap). The only required field is
     * {@code precio_venta} (Long). All other fields default to zero when absent.
     *
     * <p>The decision is immediately set to {@code ENVIADA} — the CEO "submits"
     * in one step, bypassing the normal BORRADOR → ENVIADA flow.
     *
     * @param competenciaId id of a competencia that must have {@code codigo='DEMO'}
     * @param payload       decision fields; {@code precio_venta} must be present and > 0
     * @return the persisted {@link DecisionEquipoEntity} in state {@code ENVIADA}
     * @throws ResourceNotFoundException     if the competencia does not exist
     * @throws NotDemoCompetenciaException   if the competencia is not the DEMO
     * @throws BusinessValidationException   if there is no open trimestre or CEO user is missing
     */
    @Transactional
    public DecisionEquipoEntity decisionCeo(Long competenciaId, Map<String, Object> payload) {
        // 1. Load competencia
        CompetenciaEntity comp = competenciaRepo.findById(competenciaId)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia", competenciaId));

        // 2. Guard: must be DEMO
        if (!esDemo(comp)) {
            throw new NotDemoCompetenciaException(comp.getCodigo());
        }

        // 3. Find the HUMANO team (exactly one in DEMO)
        List<EquipoEntity> humanos = equipoRepo.findByCompetenciaIdAndTipo(competenciaId, "HUMANO");
        if (humanos.isEmpty()) {
            throw new BusinessValidationException(
                    "La competencia DEMO no tiene equipo HUMANO configurado");
        }
        EquipoEntity equipoHumano = humanos.get(0);

        // 4. Find the open trimestre
        List<TrimestreEntity> abiertos = trimestreRepo.findByCompetenciaIdAndEstado(
                competenciaId, TrimestreStateMachine.ABIERTO_DECISIONES);
        if (abiertos.isEmpty()) {
            throw new BusinessValidationException(
                    "No hay trimestre ABIERTO_DECISIONES en la competencia DEMO");
        }
        TrimestreEntity trimestre = abiertos.get(0);

        // 5. Find the CEO user
        UsuarioEntity ceo = usuarioRepo.findByEmail(DemoConstants.CEO_EMAIL)
                .orElseThrow(() -> new BusinessValidationException(
                        "Usuario CEO demo no encontrado. Verifique que la migración seed esté aplicada: "
                                + DemoConstants.CEO_EMAIL));

        // 6. Build DecisionInput from payload
        DecisionInput input = buildInput(payload);

        // 7. Persist via DecisionService (bypasses member/captain validation)
        return decisionService.upsertDecisionCeo(
                equipoHumano.getId(), trimestre.getId(), input, ceo.getId());
    }

    // -----------------------------------------------------------------------
    // Avanzar
    // -----------------------------------------------------------------------

    /**
     * Closes the current open trimestre (running the simulation engine) and,
     * if a next trimestre exists, opens it (triggering bot decisions). If the
     * closed trimestre was the last one, the competition transitions to
     * {@code FINALIZADA} automatically inside {@link TrimestreService#cerrar}.
     *
     * @param competenciaId id of a competencia that must have {@code codigo='DEMO'}
     * @return summary with the ids of the closed and newly opened trimestres,
     *         and the competition's updated estado
     * @throws ResourceNotFoundException   if the competencia does not exist
     * @throws NotDemoCompetenciaException if the competencia is not the DEMO
     * @throws BusinessValidationException if there is no trimestre in ABIERTO_DECISIONES
     */
    @Transactional
    public AvanzarResult avanzar(Long competenciaId) {
        // 1. Load competencia
        CompetenciaEntity comp = competenciaRepo.findById(competenciaId)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia", competenciaId));

        // 2. Guard: must be DEMO
        if (!esDemo(comp)) {
            throw new NotDemoCompetenciaException(comp.getCodigo());
        }

        // 3. Find the open trimestre
        List<TrimestreEntity> abiertos = trimestreRepo.findByCompetenciaIdAndEstado(
                competenciaId, TrimestreStateMachine.ABIERTO_DECISIONES);
        if (abiertos.isEmpty()) {
            throw new BusinessValidationException(
                    "No hay trimestre ABIERTO_DECISIONES para avanzar");
        }
        TrimestreEntity actual = abiertos.get(0);

        // 4. Remember id and numero
        Long actualId = actual.getId();
        short numero = actual.getNumero();

        // 5. Close the current trimestre (engine processes atomically)
        trimestreService.cerrar(actualId);

        // 6. Look up the next trimestre and open it, if it exists.
        // Si no hay siguiente, el motor dejó la competencia en PENDIENTE_FINALIZAR
        // (en el flujo normal el moderador finaliza con un click). Para la DEMO
        // saltamos esa fricción y finalizamos automáticamente.
        Long siguienteId = trimestreRepo
                .findByCompetenciaIdAndNumero(competenciaId, (short) (numero + 1))
                .map(next -> {
                    trimestreService.abrir(next.getId());
                    return next.getId();
                })
                .orElse(null);

        if (siguienteId == null) {
            competenciaService.finalizar(competenciaId);
        }

        // 7. Reload competencia (estado actual tras cerrar/abrir/finalizar)
        CompetenciaEntity compReloaded = competenciaRepo.findById(competenciaId)
                .orElseThrow(() -> new IllegalStateException("Competencia disappeared after cerrar"));

        // 8. Return summary
        return new AvanzarResult(actualId, siguienteId, compReloaded.getEstado());
    }

    // -----------------------------------------------------------------------
    // Reiniciar
    // -----------------------------------------------------------------------

    /**
     * Wipes all runtime data of the DEMO competition (decisions, results, rankings,
     * events, chat, notifications, audit) but preserves equipos and miembros, then
     * recreates the trimestres in PENDIENTE and opens Q1 so the demo is immediately
     * ready to be played again.
     *
     * <p>The operation is atomic — it runs inside a single transaction. If any step
     * fails, the full rollback is triggered and the competencia stays in its previous
     * state.
     *
     * @param competenciaId id of a competencia that must have {@code codigo='DEMO'}
     * @return the reloaded {@link CompetenciaEntity} after reset (estado = {@code EN_CURSO})
     * @throws ResourceNotFoundException   if the competencia does not exist
     * @throws NotDemoCompetenciaException if the competencia is not the DEMO
     * @throws BusinessValidationException if the simulation engine is currently
     *                                     processing a trimestre (CERRADO_PROCESANDO)
     */
    @Transactional
    public CompetenciaEntity reiniciar(Long competenciaId) {
        // 1. Load competencia
        CompetenciaEntity comp = competenciaRepo.findById(competenciaId)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia", competenciaId));

        // 2. Guard: must be DEMO
        if (!esDemo(comp)) {
            throw new NotDemoCompetenciaException(comp.getCodigo());
        }

        // 3. Pessimistic lock to prevent concurrent reset/avanzar
        jdbc.queryForObject(
                "SELECT id FROM sim.competencia WHERE id = ? FOR UPDATE",
                Long.class, competenciaId);

        // 4. Reject if motor is running
        Integer procesando = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sim.trimestre WHERE competencia_id = ? AND estado = 'CERRADO_PROCESANDO'",
                Integer.class, competenciaId);
        if (procesando != null && procesando > 0) {
            throw new BusinessValidationException(
                    "Motor activo procesando un trimestre — espere a que termine");
        }

        // 5. DELETE runtime data in FK-safe order
        // 5a. coaching_trimestre (FK -> trimestre; actual table name differs from spec's "coaching_feedback")
        jdbc.update(
                "DELETE FROM sim.coaching_trimestre WHERE trimestre_id IN "
                        + "(SELECT id FROM sim.trimestre WHERE competencia_id = ?)",
                competenciaId);

        // 5b. chat_mensaje (FK -> competencia)
        jdbc.update("DELETE FROM sim.chat_mensaje WHERE competencia_id = ?", competenciaId);

        // 5c. notificacion (FK -> competencia, nullable SET NULL so rows may have NULL competencia_id)
        jdbc.update("DELETE FROM sim.notificacion WHERE competencia_id = ?", competenciaId);

        // 5d. bot_decision_log (FK -> trimestre ON DELETE CASCADE, but explicit for clarity)
        jdbc.update(
                "DELETE FROM sim.bot_decision_log WHERE trimestre_id IN "
                        + "(SELECT id FROM sim.trimestre WHERE competencia_id = ?)",
                competenciaId);

        // 5e. ranking_trimestre (FK -> competencia)
        jdbc.update("DELETE FROM sim.ranking_trimestre WHERE competencia_id = ?", competenciaId);

        // 5f. evento_competencia (actual table name; spec called it "evento_aplicado")
        jdbc.update(
                "DELETE FROM sim.evento_competencia WHERE trimestre_id IN "
                        + "(SELECT id FROM sim.trimestre WHERE competencia_id = ?)",
                competenciaId);

        // 5g. snapshot_estado (FK -> trimestre)
        jdbc.update(
                "DELETE FROM sim.snapshot_estado WHERE trimestre_id IN "
                        + "(SELECT id FROM sim.trimestre WHERE competencia_id = ?)",
                competenciaId);

        // 5h. resultado_calculo (FK -> trimestre)
        jdbc.update(
                "DELETE FROM sim.resultado_calculo WHERE trimestre_id IN "
                        + "(SELECT id FROM sim.trimestre WHERE competencia_id = ?)",
                competenciaId);

        // 5i. auditoria_decision must be deleted before decision_equipo (FK -> decision_equipo ON DELETE RESTRICT)
        jdbc.update(
                "DELETE FROM sim.auditoria_decision WHERE decision_equipo_id IN "
                        + "(SELECT de.id FROM sim.decision_equipo de "
                        + " JOIN sim.trimestre t ON t.id = de.trimestre_id "
                        + " WHERE t.competencia_id = ?)",
                competenciaId);

        // 5j. decision_equipo (actual table name; spec called it "decision")
        jdbc.update(
                "DELETE FROM sim.decision_equipo WHERE trimestre_id IN "
                        + "(SELECT id FROM sim.trimestre WHERE competencia_id = ?)",
                competenciaId);

        // 5k. auditoria_evento (FK -> competencia)
        jdbc.update("DELETE FROM sim.auditoria_evento WHERE competencia_id = ?", competenciaId);

        // 5l. trimestre (no equipo_bancarrota table; bancarrota is columns on equipo — reset below)
        jdbc.update("DELETE FROM sim.trimestre WHERE competencia_id = ?", competenciaId);

        // 6. INSERT trimestres 1..numTrimestres in PENDIENTE
        for (int i = 1; i <= comp.getNumTrimestres(); i++) {
            jdbc.update(
                    "INSERT INTO sim.trimestre (competencia_id, numero, estado) VALUES (?, ?, 'PENDIENTE')",
                    competenciaId, i);
        }

        // 7. UPDATE competencia to EN_CURSO
        jdbc.update("UPDATE sim.competencia SET estado = 'EN_CURSO', updated_at = NOW() WHERE id = ?",
                competenciaId);

        // 8. UPDATE equipos to ACTIVO (and reset bancarrota columns)
        jdbc.update(
                "UPDATE sim.equipo SET estado = 'ACTIVO', en_bancarrota = FALSE, "
                        + "trimestre_bancarrota = NULL, posicion_final = NULL, pip_final = NULL "
                        + "WHERE competencia_id = ?",
                competenciaId);

        // 9. Audit marker for the reset
        jdbc.update(
                "INSERT INTO sim.auditoria_evento "
                        + "(competencia_id, usuario_id, tipo_accion, descripcion, ocurrido_at) "
                        + "VALUES (?, NULL, 'DEMO_REINICIADA', 'Competencia DEMO reiniciada', NOW())",
                competenciaId);

        // 10. Open Q1 (triggers BotDecisionService)
        TrimestreEntity q1 = trimestreRepo
                .findByCompetenciaIdAndNumero(competenciaId, (short) 1)
                .orElseThrow(() -> new IllegalStateException(
                        "Q1 not found after reinserting trimestres for competencia " + competenciaId));

        // Snapshots INICIO de Q1 (el motor los exige al cerrar el trimestre).
        // En el flujo normal los crea CompetenciaService.iniciar(); en DEMO no
        // pasa por iniciar (la competencia ya nace EN_CURSO), así que los
        // creamos explícitamente acá tras el reset.
        List<EquipoEntity> equipos = equipoRepo.findByCompetenciaId(competenciaId);
        competenciaService.crearSnapshotsInicialesQ1(comp, equipos, q1);

        trimestreService.abrir(q1.getId());

        // 11. Reload and return the competencia (EN_CURSO after step 7)
        return competenciaRepo.findById(competenciaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Competencia disappeared after reiniciar: " + competenciaId));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Converts a loose payload map into a {@link DecisionInput}.
     * Only {@code precio_venta} is required; all other fields default to zero.
     */
    private DecisionInput buildInput(Map<String, Object> payload) {
        long precioVenta = toLong(payload, "precio_venta");
        if (precioVenta <= 0) {
            throw new BusinessValidationException(
                    "El campo 'precio_venta' es obligatorio y debe ser mayor a 0");
        }

        DecisionInput input = new DecisionInput(precioVenta);
        input.setProduccionPlanificada(toLong(payload, "produccion_planificada"));
        input.setInversionMarketing(toLong(payload, "inversion_marketing"));
        input.setInversionId(toLong(payload, "inversion_id"));
        input.setInversionCapacidad(toLong(payload, "inversion_capacidad"));
        input.setInversionCapacitacion(toLong(payload, "inversion_capacitacion"));
        input.setPrestamoSolicitado(toLong(payload, "prestamo_solicitado"));
        input.setDividendosPagar(toLong(payload, "dividendos_pagar"));
        input.setContratacionesNetas(toInt(payload, "contrataciones_netas"));
        input.setAumentoSalarialPct(toFloat(payload, "aumento_salarial_pct"));

        return input;
    }

    private long toLong(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    private int toInt(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(v.toString());
    }

    private float toFloat(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return 0f;
        if (v instanceof Number n) return n.floatValue();
        return Float.parseFloat(v.toString());
    }
}
