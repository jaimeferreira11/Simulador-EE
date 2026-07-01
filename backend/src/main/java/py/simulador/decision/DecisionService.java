package py.simulador.decision;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.api.generated.model.DecisionInput;
import py.simulador.auditoria.AuditoriaService;
import py.simulador.bot.BotDecisionDTO;
import py.simulador.catalogo.AreaDecisionEntity;
import py.simulador.catalogo.AreaDecisionRepository;
import py.simulador.common.AccessDeniedException;
import py.simulador.common.BusinessValidationException;
import py.simulador.common.InvalidStateTransitionException;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.competencia.CompetenciaStateMachine;
import py.simulador.equipo.EquipoMiembroEntity;
import py.simulador.equipo.EquipoMiembroRepository;
import py.simulador.resultado.SnapshotEstadoEntity;
import py.simulador.resultado.SnapshotEstadoRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;
import py.simulador.trimestre.TrimestreStateMachine;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static py.simulador.decision.DecisionStateMachine.*;

@Service
public class DecisionService {

    private final DecisionEquipoRepository decisionRepo;
    private final DecisionCampoLogRepository campoLogRepo;
    private final AreaDecisionRepository areaRepo;
    private final TrimestreRepository trimestreRepo;
    private final CompetenciaRepository competenciaRepo;
    private final EquipoMiembroRepository miembroRepo;
    private final SnapshotEstadoRepository snapshotRepo;
    private final AuditoriaService auditoria;
    private final ContextoDecisionService contextoDecisionService;

    public DecisionService(DecisionEquipoRepository decisionRepo,
                           DecisionCampoLogRepository campoLogRepo,
                           AreaDecisionRepository areaRepo,
                           TrimestreRepository trimestreRepo,
                           CompetenciaRepository competenciaRepo,
                           EquipoMiembroRepository miembroRepo,
                           SnapshotEstadoRepository snapshotRepo,
                           AuditoriaService auditoria,
                           ContextoDecisionService contextoDecisionService) {
        this.decisionRepo = decisionRepo;
        this.campoLogRepo = campoLogRepo;
        this.areaRepo = areaRepo;
        this.trimestreRepo = trimestreRepo;
        this.competenciaRepo = competenciaRepo;
        this.miembroRepo = miembroRepo;
        this.snapshotRepo = snapshotRepo;
        this.auditoria = auditoria;
        this.contextoDecisionService = contextoDecisionService;
    }

    @Transactional(readOnly = true)
    public DecisionEquipoEntity findByEquipoAndTrimestre(Long equipoId, Long trimestreId) {
        return decisionRepo.findByEquipoIdAndTrimestreId(equipoId, trimestreId)
                .orElseThrow(() -> new ResourceNotFoundException("DecisionEquipo",
                        "equipo_trimestre", equipoId + "/" + trimestreId));
    }

    @Transactional(readOnly = true)
    public List<DecisionEquipoEntity> findByTrimestre(Long trimestreId) {
        return decisionRepo.findByTrimestreId(trimestreId);
    }

    @Transactional(readOnly = true)
    public List<DecisionCampoLogEntity> findCampoLog(Long decisionEquipoId) {
        return campoLogRepo.findByDecisionEquipoId(decisionEquipoId);
    }

    /**
     * Crea o actualiza la decisión del equipo.
     * Valida que el jugador solo modifique campos de su área asignada.
     * El capitán (es_capitan=true) puede modificar cualquier campo.
     * Registra cada cambio en decision_campo_log.
     */
    @Transactional
    public DecisionEquipoEntity upsert(Long equipoId, Long trimestreId,
                                        DecisionInput input, Long usuarioId) {
        TrimestreEntity tri = trimestreRepo.findById(trimestreId)
                .orElseThrow(() -> new ResourceNotFoundException("Trimestre", trimestreId));

        if (!TrimestreStateMachine.ABIERTO_DECISIONES.equals(tri.getEstado())) {
            throw new InvalidStateTransitionException("trimestre", tri.getEstado(),
                    "editar decisiones", TrimestreStateMachine.transicionesValidas(tri.getEstado()));
        }

        // Verificar membresía y obtener campos permitidos según área
        EquipoMiembroEntity miembro = miembroRepo.findByEquipoIdAndUsuarioId(equipoId, usuarioId)
                .orElseThrow(() -> new AccessDeniedException("No eres miembro de este equipo"));
        Set<String> camposPermitidos = resolverCamposPermitidos(miembro);

        var existing = decisionRepo.findByEquipoIdAndTrimestreId(equipoId, trimestreId);

        DecisionEquipoEntity entity;
        boolean esNueva;
        if (existing.isPresent()) {
            entity = existing.get();
            esNueva = false;
            if (!BORRADOR.equals(entity.getEstado())) {
                throw new InvalidStateTransitionException("decision", entity.getEstado(),
                        "editar", DecisionStateMachine.transicionesValidas(entity.getEstado()));
            }
        } else {
            entity = new DecisionEquipoEntity();
            entity.setEquipoId(equipoId);
            entity.setTrimestreId(trimestreId);
            entity.setEstado(BORRADOR);
            esNueva = true;
        }

        entity.setRegistradoPorUsuarioId(usuarioId);

        // RN-DEC-007: validar rangos
        List<String> erroresValidacion = validar(equipoId, trimestreId, input);
        if (!erroresValidacion.isEmpty()) {
            throw new BusinessValidationException(erroresValidacion.get(0));
        }

        // Capturar cambios y aplicar solo campos permitidos
        List<CampoChange> cambios = mapInputConPermisos(entity, input, camposPermitidos, esNueva);

        entity.setUpdatedAt(OffsetDateTime.now());
        DecisionEquipoEntity saved = decisionRepo.save(entity);

        // Persistir auditoría (ahora el ID existe)
        registrarCambios(saved.getId(), cambios, usuarioId);

        return saved;
    }

    /**
     * Persiste o actualiza una decisión generada por un bot.
     *
     * <p>Bypassa la validación de membresía (los bots no son miembros de
     * equipo_miembro) y el flujo de permisos por área: el strategy genera
     * todos los campos. La decisión queda en estado {@code ENVIADA}
     * directamente, ya que el bot "envía" inmediatamente lo que decide.
     *
     * <p>Es idempotente: si ya existe una decisión para el equipo/trimestre,
     * se sobreescribe (last-write-wins). Esto permite re-generar decisiones
     * de bot si la apertura del trimestre se reintenta.
     *
     * @param equipoId       id del equipo bot
     * @param trimestreId    id del trimestre abierto
     * @param dto            decisión generada por el strategy
     * @param systemUserId   id del usuario sistema (system-bot@simulador.local)
     */
    @Transactional
    public DecisionEquipoEntity upsertDecisionBot(Long equipoId, Long trimestreId,
                                                   BotDecisionDTO dto, Long systemUserId) {
        // Precondición: rechazar valores groseramente inválidos producidos por
        // un strategy buggy (HeuristicStrategy hoy es seguro, pero un futuro
        // LlmBotStrategy o cambio en heurística podría romper el motor con
        // negativos o ceros donde no corresponden). Esto NO reemplaza políticas
        // de negocio (precio mínimo, etc.); sólo bloquea errores groseros.
        validarDtoBot(dto);

        // Campos que el DTO Phase 1 no produce: dejarlos en 0/defaults
        DecisionInput input = new DecisionInput(dto.precioUnitario())
                .produccionPlanificada(dto.produccionUnidades())
                .inversionMarketing(dto.inversionMarketing())
                .inversionId(dto.inversionRd())
                .prestamoSolicitado(dto.prestamoSolicitado())
                .dividendosPagar(0L)
                .inversionCapacidad(0L)
                .inversionCapacitacion(0L)
                .contratacionesNetas(0)
                .aumentoSalarialPct(0f);

        return upsertWithExplicitUser(equipoId, trimestreId, input, systemUserId,
                "generar decisión de bot", "regenerar decisión de bot");
    }

    /**
     * Valida que el {@link BotDecisionDTO} no contenga valores groseramente
     * inválidos antes de persistir. Replica el espíritu de {@link #validar}
     * para humanos pero en versión liviana: sólo rechaza errores que romperían
     * el motor (negativos donde no corresponden, precios ≤ 0, plantilla ≤ 0).
     *
     * <p>No valida reglas de negocio (precio mínimo, salario mínimo MTESS,
     * etc.) — esas viven en otras capas y son responsabilidad del strategy.
     */
    private void validarDtoBot(BotDecisionDTO dto) {
        if (dto.precioUnitario() <= 0) {
            throw new BusinessValidationException(
                    "BotDecisionDTO inválido: precio unitario debe ser mayor a 0");
        }
        if (dto.produccionUnidades() < 0) {
            throw new BusinessValidationException(
                    "BotDecisionDTO inválido: produccion no puede ser negativa");
        }
        if (dto.inversionMarketing() < 0) {
            throw new BusinessValidationException(
                    "BotDecisionDTO inválido: inversion en marketing no puede ser negativa");
        }
        if (dto.inversionRd() < 0) {
            throw new BusinessValidationException(
                    "BotDecisionDTO inválido: inversion en I+D no puede ser negativa");
        }
        if (dto.cantidadEmpleados() <= 0) {
            throw new BusinessValidationException(
                    "BotDecisionDTO inválido: cantidad de empleados debe ser mayor a 0");
        }
        if (dto.salarioPromedio() < 0) {
            throw new BusinessValidationException(
                    "BotDecisionDTO inválido: salario promedio no puede ser negativo");
        }
        if (dto.prestamoSolicitado() < 0) {
            throw new BusinessValidationException(
                    "BotDecisionDTO inválido: prestamo solicitado no puede ser negativo");
        }
        if (dto.inversionFinanciera() < 0) {
            throw new BusinessValidationException(
                    "BotDecisionDTO inválido: inversion financiera no puede ser negativa");
        }
    }

    @Transactional
    public DecisionEquipoEntity enviar(Long equipoId, Long trimestreId, Long usuarioId) {
        DecisionEquipoEntity decision = findByEquipoAndTrimestre(equipoId, trimestreId);
        DecisionStateMachine.validarTransicion(decision.getEstado(), ENVIADA);

        TrimestreEntity tri = trimestreRepo.findById(trimestreId)
                .orElseThrow(() -> new ResourceNotFoundException("Trimestre", trimestreId));
        if (!TrimestreStateMachine.ABIERTO_DECISIONES.equals(tri.getEstado())) {
            throw new BusinessValidationException(
                    "El trimestre no esta abierto para decisiones");
        }

        CompetenciaEntity comp = competenciaRepo.findById(tri.getCompetenciaId())
                .orElseThrow(() -> new IllegalStateException("Competencia not found"));
        if (CompetenciaStateMachine.PAUSADA.equals(comp.getEstado())) {
            throw new BusinessValidationException(
                    "No se pueden enviar decisiones mientras la competencia esta pausada");
        }

        EquipoMiembroEntity miembro = miembroRepo.findByEquipoIdAndUsuarioId(equipoId, usuarioId)
                .orElseThrow(() -> new AccessDeniedException(
                        "No eres miembro de este equipo"));
        if (!miembro.isEsCapitan()) {
            throw new AccessDeniedException(
                    "Solo el capitan puede enviar la decision");
        }

        // RN-DEC-004: validar caja proyectada antes de enviar
        validarCajaProyectada(equipoId, trimestreId, decision);

        decision.setEstado(ENVIADA);
        decision.setSubmittedAt(OffsetDateTime.now());
        decision.setUpdatedAt(OffsetDateTime.now());
        DecisionEquipoEntity saved = decisionRepo.save(decision);
        auditoria.registrar(comp.getId(), usuarioId, "DECISION_ENVIADA",
                "Equipo #" + equipoId + " envió su decisión para Q" + tri.getNumero());
        return saved;
    }

    /**
     * Persiste o actualiza una decisión en nombre de un usuario fantoche (demo CEO).
     *
     * <p>Bypassa la validación de membresía y el flujo de permisos por área,
     * igual que {@link #upsertDecisionBot}. La decisión queda en estado
     * {@code ENVIADA} directamente.
     *
     * <p>Idempotente: si ya existe una decisión para el equipo/trimestre,
     * se sobreescribe (last-write-wins), siempre que no esté {@code PROCESADA}.
     *
     * @param equipoId    id del equipo HUMANO de la competencia DEMO
     * @param trimestreId id del trimestre ABIERTO_DECISIONES
     * @param input       campos de la decisión; {@code precio_venta} obligatorio
     * @param usuarioId   id del usuario fantoche (ceo.demo@simulador.py)
     */
    @Transactional
    public DecisionEquipoEntity upsertDecisionCeo(Long equipoId, Long trimestreId,
                                                   DecisionInput input, Long usuarioId) {
        return upsertWithExplicitUser(equipoId, trimestreId, input, usuarioId,
                "persistir decisión CEO demo", "reescribir decisión CEO demo");
    }

    /**
     * Núcleo compartido por {@link #upsertDecisionBot} y {@link #upsertDecisionCeo}.
     *
     * <p>Bypassa la validación de membresía y permisos por área. Valida el
     * estado del trimestre, aplica todos los campos del input y persiste la
     * decisión en estado {@code ENVIADA} (idempotente, last-write-wins, excepto
     * si ya está {@code PROCESADA}).
     */
    private DecisionEquipoEntity upsertWithExplicitUser(Long equipoId, Long trimestreId,
                                                         DecisionInput input, Long usuarioId,
                                                         String accionTrimestre,
                                                         String accionDecision) {
        TrimestreEntity tri = trimestreRepo.findById(trimestreId)
                .orElseThrow(() -> new ResourceNotFoundException("Trimestre", trimestreId));

        if (!TrimestreStateMachine.ABIERTO_DECISIONES.equals(tri.getEstado())) {
            throw new InvalidStateTransitionException("trimestre", tri.getEstado(),
                    accionTrimestre,
                    TrimestreStateMachine.transicionesValidas(tri.getEstado()));
        }

        List<String> errores = validar(equipoId, trimestreId, input);
        if (!errores.isEmpty()) {
            throw new BusinessValidationException(errores.get(0));
        }

        var existing = decisionRepo.findByEquipoIdAndTrimestreId(equipoId, trimestreId);

        DecisionEquipoEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            if (PROCESADA.equals(entity.getEstado())) {
                throw new InvalidStateTransitionException("decision", entity.getEstado(),
                        accionDecision,
                        DecisionStateMachine.transicionesValidas(entity.getEstado()));
            }
        } else {
            entity = new DecisionEquipoEntity();
            entity.setEquipoId(equipoId);
            entity.setTrimestreId(trimestreId);
        }

        entity.setRegistradoPorUsuarioId(usuarioId);
        entity.setPrecioVenta(input.getPrecioVenta());
        entity.setProduccionPlanificada(input.getProduccionPlanificada() != null ? input.getProduccionPlanificada() : 0L);
        entity.setInversionMarketing(input.getInversionMarketing() != null ? input.getInversionMarketing() : 0L);
        entity.setInversionId(input.getInversionId() != null ? input.getInversionId() : 0L);
        entity.setInversionCapacidad(input.getInversionCapacidad() != null ? input.getInversionCapacidad() : 0L);
        entity.setInversionCapacitacion(input.getInversionCapacitacion() != null ? input.getInversionCapacitacion() : 0L);
        entity.setPrestamoSolicitado(input.getPrestamoSolicitado() != null ? input.getPrestamoSolicitado() : 0L);
        entity.setDividendosPagar(input.getDividendosPagar() != null ? input.getDividendosPagar() : 0L);
        entity.setContratacionesNetas(input.getContratacionesNetas() != null
                ? input.getContratacionesNetas().shortValue() : (short) 0);
        entity.setAumentoSalarialPct(input.getAumentoSalarialPct() != null
                ? BigDecimal.valueOf(input.getAumentoSalarialPct()) : BigDecimal.ZERO);

        OffsetDateTime ahora = OffsetDateTime.now();
        entity.setEstado(ENVIADA);
        entity.setSubmittedAt(ahora);
        entity.setUpdatedAt(ahora);

        return decisionRepo.save(entity);
    }

    @Transactional
    public DecisionEquipoEntity reabrir(Long equipoId, Long trimestreId) {
        DecisionEquipoEntity decision = findByEquipoAndTrimestre(equipoId, trimestreId);

        if (BORRADOR.equals(decision.getEstado())) {
            return decision;
        }

        DecisionStateMachine.validarTransicion(decision.getEstado(), BORRADOR);

        TrimestreEntity tri = trimestreRepo.findById(trimestreId)
                .orElseThrow(() -> new ResourceNotFoundException("Trimestre", trimestreId));
        if (TrimestreStateMachine.PROCESADO.equals(tri.getEstado())) {
            throw new BusinessValidationException(
                    "No se puede reabrir una decision de un trimestre ya procesado");
        }

        decision.setEstado(BORRADOR);
        decision.setSubmittedAt(null);
        decision.setUpdatedAt(OffsetDateTime.now());
        return decisionRepo.save(decision);
    }

    /**
     * Valida rangos de los campos de decisión (RN-DEC-007).
     * Se ejecuta tanto al guardar como al enviar.
     */
    @Transactional(readOnly = true)
    public List<String> validar(Long equipoId, Long trimestreId, DecisionInput input) {
        List<String> errores = new ArrayList<>();

        // Precio obligatorio y > 0
        if (input.getPrecioVenta() <= 0) {
            errores.add("El precio de venta debe ser mayor a 0");
        }

        // Campos no pueden ser negativos (excepto contrataciones_netas que permite despidos)
        if (input.getProduccionPlanificada() != null && input.getProduccionPlanificada() < 0) {
            errores.add("La produccion planificada no puede ser negativa");
        }
        if (input.getInversionMarketing() != null && input.getInversionMarketing() < 0) {
            errores.add("La inversion en marketing no puede ser negativa");
        }
        if (input.getInversionId() != null && input.getInversionId() < 0) {
            errores.add("La inversion en I+D no puede ser negativa");
        }
        if (input.getInversionCapacidad() != null && input.getInversionCapacidad() < 0) {
            errores.add("La inversion en capacidad no puede ser negativa");
        }
        if (input.getInversionCapacitacion() != null && input.getInversionCapacitacion() < 0) {
            errores.add("La inversion en capacitacion no puede ser negativa");
        }
        if (input.getPrestamoSolicitado() != null && input.getPrestamoSolicitado() < 0) {
            errores.add("El prestamo solicitado no puede ser negativo");
        }
        if (input.getDividendosPagar() != null && input.getDividendosPagar() < 0) {
            errores.add("Los dividendos a pagar no pueden ser negativos");
        }
        if (input.getAumentoSalarialPct() != null && input.getAumentoSalarialPct() < 0) {
            errores.add("El aumento salarial no puede ser negativo (no se permiten reducciones)");
        }

        return errores;
    }

    /**
     * Proyecta la caja al cierre para validar que no sea negativa (RN-DEC-004).
     * Delega en {@link ContextoDecisionService#calcularProyeccion} para usar EXACTAMENTE la
     * misma fórmula que ve el jugador en el what-if (que a su vez espeja al motor), de modo
     * que el número mostrado y el que bloquea el envío siempre coincidan.
     */
    private void validarCajaProyectada(Long equipoId, Long trimestreId, DecisionEquipoEntity dec) {
        SnapshotEstadoEntity snap = snapshotRepo
                .findByEquipoIdAndTrimestreIdAndMomento(equipoId, trimestreId, "INICIO")
                .orElse(null);
        if (snap == null) return; // sin snapshot no podemos validar

        DecisionInput input = new DecisionInput();
        input.setPrecioVenta(dec.getPrecioVenta());
        input.setProduccionPlanificada(dec.getProduccionPlanificada());
        input.setInversionCapacidad(dec.getInversionCapacidad());
        input.setInversionMarketing(dec.getInversionMarketing());
        input.setInversionId(dec.getInversionId());
        input.setInversionCapacitacion(dec.getInversionCapacitacion());
        input.setPrestamoSolicitado(dec.getPrestamoSolicitado());
        input.setDividendosPagar(dec.getDividendosPagar());
        input.setContratacionesNetas((int) dec.getContratacionesNetas());
        input.setAumentoSalarialPct(dec.getAumentoSalarialPct() != null
                ? dec.getAumentoSalarialPct().floatValue() : 0f);

        long cajaProyectada = contextoDecisionService
                .calcularProyeccion(equipoId, trimestreId, input)
                .cajaProyectada();

        if (cajaProyectada < 0) {
            throw new BusinessValidationException(
                    "La caja proyectada seria negativa (Gs. "
                            + String.format("%,d", cajaProyectada)
                            + "). Reduce inversiones, dividendos, o solicita un prestamo.");
        }
    }

    // ========================================================================
    // Permisos por área
    // ========================================================================

    /** Todos los campos de decisión editables */
    private static final Set<String> TODOS_LOS_CAMPOS = Set.of(
            "prestamo_solicitado", "dividendos_pagar",
            "produccion_planificada", "inversion_capacidad", "inversion_id",
            "precio_venta", "inversion_marketing",
            "contrataciones_netas", "aumento_salarial_pct", "inversion_capacitacion"
    );

    /**
     * Capitán → todos los campos.
     * Jugador con área → campos definidos en area_decision.campos.
     * Jugador sin área → error.
     */
    private Set<String> resolverCamposPermitidos(EquipoMiembroEntity miembro) {
        if (miembro.isEsCapitan()) {
            return TODOS_LOS_CAMPOS;
        }
        if (miembro.getAreaId() == null) {
            throw new BusinessValidationException(
                    "No tienes un área asignada. Contacta al moderador.");
        }
        AreaDecisionEntity area = areaRepo.findById(miembro.getAreaId())
                .orElseThrow(() -> new IllegalStateException(
                        "Área no encontrada: " + miembro.getAreaId()));
        return new HashSet<>(Arrays.asList(area.getCampos()));
    }

    // ========================================================================
    // Mapeo con permisos y detección de cambios
    // ========================================================================

    /** Registro temporal de un cambio detectado */
    private record CampoChange(String campo, String valorAnterior, String valorNuevo) {}

    /**
     * Aplica los campos del input a la entidad respetando permisos de área.
     * Retorna la lista de cambios detectados para auditoría.
     */
    private List<CampoChange> mapInputConPermisos(DecisionEquipoEntity entity, DecisionInput input,
                                                    Set<String> camposPermitidos, boolean esNueva) {
        List<CampoChange> cambios = new ArrayList<>();

        applyLong(entity, "precio_venta", entity.getPrecioVenta(),
                input.getPrecioVenta(), camposPermitidos, esNueva, cambios,
                (e, v) -> e.setPrecioVenta(v));

        applyLong(entity, "produccion_planificada", entity.getProduccionPlanificada(),
                input.getProduccionPlanificada() != null ? input.getProduccionPlanificada() : 0L,
                camposPermitidos, esNueva, cambios,
                (e, v) -> e.setProduccionPlanificada(v));

        applyLong(entity, "inversion_marketing", entity.getInversionMarketing(),
                input.getInversionMarketing() != null ? input.getInversionMarketing() : 0L,
                camposPermitidos, esNueva, cambios,
                (e, v) -> e.setInversionMarketing(v));

        applyLong(entity, "inversion_id", entity.getInversionId(),
                input.getInversionId() != null ? input.getInversionId() : 0L,
                camposPermitidos, esNueva, cambios,
                (e, v) -> e.setInversionId(v));

        applyLong(entity, "inversion_capacidad", entity.getInversionCapacidad(),
                input.getInversionCapacidad() != null ? input.getInversionCapacidad() : 0L,
                camposPermitidos, esNueva, cambios,
                (e, v) -> e.setInversionCapacidad(v));

        applyLong(entity, "inversion_capacitacion", entity.getInversionCapacitacion(),
                input.getInversionCapacitacion() != null ? input.getInversionCapacitacion() : 0L,
                camposPermitidos, esNueva, cambios,
                (e, v) -> e.setInversionCapacitacion(v));

        applyLong(entity, "prestamo_solicitado", entity.getPrestamoSolicitado(),
                input.getPrestamoSolicitado() != null ? input.getPrestamoSolicitado() : 0L,
                camposPermitidos, esNueva, cambios,
                (e, v) -> e.setPrestamoSolicitado(v));

        applyLong(entity, "dividendos_pagar", entity.getDividendosPagar(),
                input.getDividendosPagar() != null ? input.getDividendosPagar() : 0L,
                camposPermitidos, esNueva, cambios,
                (e, v) -> e.setDividendosPagar(v));

        // contrataciones_netas: short, tratar como long para uniformidad
        applyLong(entity, "contrataciones_netas",
                (long) entity.getContratacionesNetas(),
                input.getContratacionesNetas() != null ? input.getContratacionesNetas().longValue() : 0L,
                camposPermitidos, esNueva, cambios,
                (e, v) -> e.setContratacionesNetas(v.shortValue()));

        // aumento_salarial_pct: BigDecimal
        if (camposPermitidos.contains("aumento_salarial_pct")) {
            BigDecimal anterior = entity.getAumentoSalarialPct() != null
                    ? entity.getAumentoSalarialPct() : BigDecimal.ZERO;
            BigDecimal nuevo = input.getAumentoSalarialPct() != null
                    ? BigDecimal.valueOf(input.getAumentoSalarialPct()) : BigDecimal.ZERO;
            if (esNueva || anterior.compareTo(nuevo) != 0) {
                cambios.add(new CampoChange("aumento_salarial_pct",
                        anterior.toPlainString(), nuevo.toPlainString()));
            }
            entity.setAumentoSalarialPct(nuevo);
        }

        return cambios;
    }

    /** Aplica un campo long si está permitido; registra cambio si difiere */
    private void applyLong(DecisionEquipoEntity entity, String campo,
                           long valorAnterior, long valorNuevo,
                           Set<String> permitidos, boolean esNueva,
                           List<CampoChange> cambios, LongSetter setter) {
        if (!permitidos.contains(campo)) return;
        if (esNueva || valorAnterior != valorNuevo) {
            cambios.add(new CampoChange(campo,
                    String.valueOf(valorAnterior), String.valueOf(valorNuevo)));
        }
        setter.set(entity, valorNuevo);
    }

    /** Persiste los logs de cambio con el ID de la decisión ya asignado */
    private void registrarCambios(Long decisionId, List<CampoChange> cambios, Long usuarioId) {
        OffsetDateTime ahora = OffsetDateTime.now();
        for (CampoChange c : cambios) {
            DecisionCampoLogEntity log = new DecisionCampoLogEntity();
            log.setDecisionEquipoId(decisionId);
            log.setCampo(c.campo());
            log.setValorAnterior(c.valorAnterior());
            log.setValorNuevo(c.valorNuevo());
            log.setUsuarioId(usuarioId);
            log.setModificadoAt(ahora);
            campoLogRepo.save(log);
        }
    }

    @FunctionalInterface
    private interface LongSetter {
        void set(DecisionEquipoEntity entity, Long valor);
    }
}
