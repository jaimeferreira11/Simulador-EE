package py.simulador.competencia;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.api.generated.model.CompetenciaCreate;
import py.simulador.api.generated.model.CompetenciaUpdate;
import py.simulador.auditoria.AuditoriaService;
import py.simulador.catalogo.EventoCatalogoEntity;
import py.simulador.catalogo.EventoCatalogoRepository;
import py.simulador.catalogo.RubroRepository;
import py.simulador.common.BusinessValidationException;
import py.simulador.common.InvalidStateTransitionException;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoMiembroRepository;
import py.simulador.equipo.EquipoRepository;
import py.simulador.equipo.EquipoService;
import py.simulador.escenario.EscenarioEventoEntity;
import py.simulador.escenario.EscenarioEventoRepository;
import py.simulador.escenario.EscenarioPredefinidoEntity;
import py.simulador.escenario.EscenarioPredefinidoRepository;
import py.simulador.evento.EventoCompetenciaEntity;
import py.simulador.evento.EventoCompetenciaRepository;
import py.simulador.llm.EventoContext;
import py.simulador.llm.NarrativaService;
import py.simulador.resultado.RankingTrimestreEntity;
import py.simulador.resultado.RankingTrimestreRepository;
import py.simulador.resultado.SnapshotEstadoEntity;
import py.simulador.resultado.SnapshotEstadoRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;
import py.simulador.trimestre.TrimestreStateMachine;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static py.simulador.competencia.CompetenciaStateMachine.*;

@Service
public class CompetenciaService {

    private final CompetenciaRepository competenciaRepo;
    private final EquipoRepository equipoRepo;
    private final EquipoMiembroRepository miembroRepo;
    private final EquipoService equipoService;
    private final TrimestreRepository trimestreRepo;
    private final SnapshotEstadoRepository snapshotRepo;
    private final RankingTrimestreRepository rankingRepo;
    private final AuditoriaService auditoria;
    private final EscenarioPredefinidoRepository escenarioRepo;
    private final EscenarioEventoRepository escenarioEventoRepo;
    private final EventoCompetenciaRepository eventoCompRepo;
    private final EventoCatalogoRepository eventoCatalogoRepo;
    private final RubroRepository rubroRepo;
    private final NarrativaService narrativaService;

    public CompetenciaService(CompetenciaRepository competenciaRepo,
                              EquipoRepository equipoRepo,
                              EquipoMiembroRepository miembroRepo,
                              EquipoService equipoService,
                              TrimestreRepository trimestreRepo,
                              SnapshotEstadoRepository snapshotRepo,
                              RankingTrimestreRepository rankingRepo,
                              AuditoriaService auditoria,
                              EscenarioPredefinidoRepository escenarioRepo,
                              EscenarioEventoRepository escenarioEventoRepo,
                              EventoCompetenciaRepository eventoCompRepo,
                              EventoCatalogoRepository eventoCatalogoRepo,
                              RubroRepository rubroRepo,
                              NarrativaService narrativaService) {
        this.competenciaRepo = competenciaRepo;
        this.equipoRepo = equipoRepo;
        this.miembroRepo = miembroRepo;
        this.equipoService = equipoService;
        this.trimestreRepo = trimestreRepo;
        this.snapshotRepo = snapshotRepo;
        this.rankingRepo = rankingRepo;
        this.auditoria = auditoria;
        this.escenarioRepo = escenarioRepo;
        this.escenarioEventoRepo = escenarioEventoRepo;
        this.eventoCompRepo = eventoCompRepo;
        this.eventoCatalogoRepo = eventoCatalogoRepo;
        this.rubroRepo = rubroRepo;
        this.narrativaService = narrativaService;
    }

    /**
     * Lista competencias filtradas por rol del usuario:
     * - ADMIN_PLATAFORMA: ve todas
     * - MODERADOR: solo las que creó (moderador_id = userId), con filtros opcionales
     * - JUGADOR: solo las donde es miembro de un equipo
     * Opcionalmente filtra por estado, entidad_id y año de creación.
     */
    @Transactional(readOnly = true)
    public List<CompetenciaEntity> findAll(String estado, String rol, Long userId) {
        return findAll(estado, rol, userId, null, null, 0, 50);
    }

    @Transactional(readOnly = true)
    public List<CompetenciaEntity> findAll(String estado, String rol, Long userId,
                                            Long entidadId, Integer anio,
                                            int page, int size) {
        if ("ADMIN_PLATAFORMA".equals(rol)) {
            return estado != null ? competenciaRepo.findByEstado(estado)
                    : (List<CompetenciaEntity>) competenciaRepo.findAll();
        }
        if ("MODERADOR".equals(rol)) {
            return competenciaRepo.findByModeradorFiltered(
                    userId, entidadId, estado, anio, size, page * size);
        }
        // JUGADOR: competencias donde participa
        return competenciaRepo.findByMiembroUsuarioId(userId);
    }

    @Transactional(readOnly = true)
    public long countAll(String estado, String rol, Long userId,
                          Long entidadId, Integer anio) {
        if ("MODERADOR".equals(rol)) {
            return competenciaRepo.countByModeradorFiltered(userId, entidadId, estado, anio);
        }
        // For other roles, count from list (already filtered)
        return findAll(estado, rol, userId).size();
    }

    @Transactional(readOnly = true)
    public CompetenciaEntity findById(Long id) {
        return competenciaRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia", id));
    }

    @Transactional(readOnly = true)
    public CompetenciaEntity findByCodigo(String codigo) {
        return competenciaRepo.findByCodigo(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia", "codigo", codigo));
    }

    @Transactional
    public CompetenciaEntity create(CompetenciaCreate input, Long moderadorId) {
        // If escenario_id is provided, apply scenario parameters
        EscenarioPredefinidoEntity escenario = null;
        Long escenarioId = input.getEscenarioId() != null && input.getEscenarioId().isPresent()
                ? input.getEscenarioId().get() : null;
        if (escenarioId != null) {
            escenario = escenarioRepo.findById(escenarioId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "EscenarioPredefinido", escenarioId));
            if (!escenario.isActivo()) {
                throw new BusinessValidationException("El escenario seleccionado no esta activo");
            }
        }

        CompetenciaEntity entity = new CompetenciaEntity();
        entity.setCodigo(generateCodigo());
        entity.setNombre(input.getNombre());
        entity.setRubroId(input.getRubroId());
        entity.setParametroMacroId(input.getParametroMacroId());
        entity.setParametroRubroId(input.getParametroRubroId());
        entity.setModeradorId(moderadorId);
        entity.setEntidadId(input.getEntidadId());

        if (escenario != null) {
            // Scenario overrides initial parameters
            entity.setNumTrimestres((short) escenario.getNumTrimestres());
            entity.setCajaInicial(escenario.getCajaInicial());
            entity.setCapacidadInicial(escenario.getCapacidadInicial());
            entity.setHeadcountInicial((short) escenario.getHeadcountInicial());
            entity.setSalarioInicial(escenario.getSalarioInicial());
            entity.setInventarioInicial(escenario.getInventarioInicial());
            entity.setValorPlantaInicial(escenario.getValorPlantaInicial());
        } else {
            entity.setNumTrimestres(input.getNumTrimestres().shortValue());
            entity.setCajaInicial(input.getCajaInicial());
            entity.setCapacidadInicial(input.getCapacidadInicial());
            entity.setHeadcountInicial(input.getHeadcountInicial().shortValue());
            entity.setSalarioInicial(input.getSalarioInicial());
            entity.setInventarioInicial(input.getInventarioInicial() != null ? input.getInventarioInicial() : 0L);
            entity.setValorPlantaInicial(input.getValorPlantaInicial());
        }

        entity.setNumEquiposMax(input.getNumEquiposMax() != null ? input.getNumEquiposMax().shortValue() : (short) 12);
        Integer maxIntegrantes = input.getMaxIntegrantesEquipo() != null && input.getMaxIntegrantesEquipo().isPresent()
                ? input.getMaxIntegrantesEquipo().get() : null;
        entity.setMaxIntegrantesEquipo(maxIntegrantes != null ? maxIntegrantes.shortValue() : null);
        entity.setBancarrotaHabilitada(
                input.getBancarrotaHabilitada() != null && input.getBancarrotaHabilitada());
        entity.setIaHabilitada(
                input.getIaHabilitada() != null && input.getIaHabilitada());
        entity.setEstado(BORRADOR);
        CompetenciaEntity saved = competenciaRepo.save(entity);

        // Schedule scenario events
        if (escenario != null) {
            programarEventosEscenario(saved, escenario, moderadorId);
        }

        auditoria.registrar(saved.getId(), moderadorId, "COMPETENCIA_CREADA",
                "Se creó la competencia " + saved.getNombre()
                        + (escenario != null ? " (escenario: " + escenario.getNombre() + ")" : ""));
        return saved;
    }

    /**
     * Pre-programs scenario events. Since trimestres are created on iniciar(),
     * we store the events with trimestre_id=null and trimestre_numero.
     * They will be materialized when the competition starts.
     * Actually, since EventoCompetenciaEntity requires trimestre_id,
     * we store them in a holding pattern and create them in iniciar().
     */
    private void programarEventosEscenario(CompetenciaEntity competencia,
                                            EscenarioPredefinidoEntity escenario,
                                            Long moderadorId) {
        // Store escenario_id on competencia for iniciar() to pick up
        competencia.setEscenarioId(escenario.getId());
        competenciaRepo.save(competencia);
    }

    @Transactional
    public CompetenciaEntity update(Long id, CompetenciaUpdate input) {
        CompetenciaEntity entity = findById(id);
        if (!BORRADOR.equals(entity.getEstado())) {
            throw new InvalidStateTransitionException("competencia", entity.getEstado(),
                    "editar", CompetenciaStateMachine.transicionesValidas(entity.getEstado()));
        }
        if (input.getNombre() != null) {
            entity.setNombre(input.getNombre());
        }
        if (input.getBancarrotaHabilitada() != null) {
            entity.setBancarrotaHabilitada(input.getBancarrotaHabilitada());
        }
        if (input.getMaxIntegrantesEquipo() != null && input.getMaxIntegrantesEquipo().isPresent()) {
            Integer max = input.getMaxIntegrantesEquipo().get();
            entity.setMaxIntegrantesEquipo(max != null ? max.shortValue() : null);
        }
        return competenciaRepo.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        CompetenciaEntity entity = findById(id);
        CompetenciaStateMachine.validarTransicion(entity.getEstado(), ARCHIVADA);
        entity.setEstado(ARCHIVADA);
        competenciaRepo.save(entity);
    }

    @Transactional
    public CompetenciaEntity abrirInscripcion(Long id) {
        CompetenciaEntity entity = findById(id);
        CompetenciaStateMachine.validarTransicion(entity.getEstado(), ABIERTA_INSCRIPCION);
        entity.setEstado(ABIERTA_INSCRIPCION);
        CompetenciaEntity saved = competenciaRepo.save(entity);
        auditoria.registrar(id, null, "INSCRIPCION_ABIERTA",
                "Se abrió la inscripción para equipos");
        return saved;
    }

    @Transactional
    public CompetenciaEntity iniciar(Long id) {
        CompetenciaEntity comp = findById(id);
        CompetenciaStateMachine.validarTransicion(comp.getEstado(), EN_CURSO);

        List<EquipoEntity> equipos = equipoRepo.findByCompetenciaId(id);
        if (equipos.size() < 2) {
            throw new BusinessValidationException(
                    "Se necesitan al menos 2 equipos para iniciar la competencia");
        }

        for (EquipoEntity equipo : equipos) {
            // Equipos BOT no tienen miembros (RN-BOT-004) — saltear validacion y auto-capitan
            if (equipo.esBot()) {
                continue;
            }
            List<py.simulador.equipo.EquipoMiembroEntity> miembros =
                    miembroRepo.findByEquipoId(equipo.getId());
            if (miembros.isEmpty()) {
                throw new BusinessValidationException(
                        "El equipo '" + equipo.getNombreEmpresa() + "' no tiene miembros");
            }
            // Auto-assign captain if none exists
            equipoService.autoAssignCapitanIfMissing(equipo.getId());
        }

        // Create N trimestres
        for (short q = 1; q <= comp.getNumTrimestres(); q++) {
            TrimestreEntity tri = new TrimestreEntity();
            tri.setCompetenciaId(id);
            tri.setNumero(q);
            tri.setEstado(TrimestreStateMachine.PENDIENTE);
            trimestreRepo.save(tri);
        }

        // Create INICIO snapshots for Q1
        TrimestreEntity q1 = trimestreRepo.findByCompetenciaIdAndNumero(id, (short) 1)
                .orElseThrow(() -> new IllegalStateException("Q1 no encontrado tras creacion"));

        crearSnapshotsInicialesQ1(comp, equipos, q1);

        comp.setEstado(EN_CURSO);
        comp.setInicioAt(OffsetDateTime.now());
        CompetenciaEntity saved = competenciaRepo.save(comp);

        // Materialize scenario events now that trimestres exist
        if (saved.getEscenarioId() != null) {
            materializarEventosEscenario(saved);
        }

        auditoria.registrar(id, null, "COMPETENCIA_INICIADA",
                "Se inició la competencia con " + equipos.size() + " equipos inscritos");
        return saved;
    }

    private void materializarEventosEscenario(CompetenciaEntity competencia) {
        List<EscenarioEventoEntity> escEventos = escenarioEventoRepo
                .findByEscenarioId(competencia.getEscenarioId());

        for (EscenarioEventoEntity escEvento : escEventos) {
            TrimestreEntity tri = trimestreRepo
                    .findByCompetenciaIdAndNumero(competencia.getId(), (short) escEvento.getTrimestreNumero())
                    .orElse(null);
            if (tri == null) continue; // Scenario event for a Q beyond this competition's range

            EventoCatalogoEntity catalogo = eventoCatalogoRepo.findById(escEvento.getEventoCatalogoId())
                    .orElse(null);
            if (catalogo == null) continue;

            EventoCompetenciaEntity evento = new EventoCompetenciaEntity();
            evento.setCompetenciaId(competencia.getId());
            evento.setTrimestreId(tri.getId());
            evento.setEventoCatalogoId(escEvento.getEventoCatalogoId());
            evento.setOrigen("ESCENARIO");
            evento.setMagnitudAplicada(catalogo.getMagnitudDefault());
            evento.setDuracionAplicada(catalogo.getDuracionQ());
            evento.setJustificacion("Evento programado del escenario");
            EventoCompetenciaEntity saved = eventoCompRepo.save(evento);

            // Generate AI narrative if enabled
            if (competencia.isIaHabilitada()) {
                String rubroNombre = rubroRepo.findById(competencia.getRubroId())
                        .map(r -> r.getNombre()).orElse("General");
                var ctx = new EventoContext(
                        catalogo.getNombre(), catalogo.getDescripcion(),
                        catalogo.getTipoEfecto(), saved.getMagnitudAplicada(),
                        saved.getDuracionAplicada(), catalogo.getSeveridad(),
                        rubroNombre, escEvento.getTrimestreNumero()
                );
                String narrativa = narrativaService.generarNarrativa(ctx);
                saved.setJustificacion(narrativa);
                eventoCompRepo.save(saved);
            }
        }
    }

    @Transactional
    public CompetenciaEntity pausar(Long id) {
        CompetenciaEntity entity = findById(id);
        if (PAUSADA.equals(entity.getEstado())) {
            return entity;
        }
        CompetenciaStateMachine.validarTransicion(entity.getEstado(), PAUSADA);

        List<TrimestreEntity> procesando = trimestreRepo.findByCompetenciaIdAndEstado(
                id, TrimestreStateMachine.CERRADO_PROCESANDO);
        if (!procesando.isEmpty()) {
            throw new BusinessValidationException(
                    "No se puede pausar mientras el motor esta procesando un trimestre");
        }

        entity.setEstado(PAUSADA);
        return competenciaRepo.save(entity);
    }

    @Transactional
    public CompetenciaEntity reanudar(Long id) {
        CompetenciaEntity entity = findById(id);
        CompetenciaStateMachine.validarTransicion(entity.getEstado(), EN_CURSO);
        entity.setEstado(EN_CURSO);
        return competenciaRepo.save(entity);
    }

    @Transactional
    public CompetenciaEntity finalizar(Long id) {
        CompetenciaEntity entity = findById(id);

        if (FINALIZADA.equals(entity.getEstado())) {
            return entity;
        }

        CompetenciaStateMachine.validarTransicion(entity.getEstado(), FINALIZADA);

        // Assign final positions from the last processed quarter's ranking
        trimestreRepo.findUltimoTrimestreProcesado(id).ifPresent(lastTri -> {
            List<RankingTrimestreEntity> rankings = rankingRepo
                    .findByCompetenciaIdAndTrimestreId(id, lastTri.getId());
            Map<Long, EquipoEntity> equipoMap = equipoRepo.findByCompetenciaId(id).stream()
                    .collect(Collectors.toMap(EquipoEntity::getId, e -> e));

            for (RankingTrimestreEntity rank : rankings) {
                EquipoEntity equipo = equipoMap.get(rank.getEquipoId());
                if (equipo != null) {
                    equipo.setPosicionFinal((short) rank.getPosicion());
                    equipo.setPipFinal(rank.getPipAcumulado());
                    equipoRepo.save(equipo);
                }
            }
        });

        entity.setEstado(FINALIZADA);
        entity.setCierreAt(OffsetDateTime.now());
        CompetenciaEntity saved = competenciaRepo.save(entity);
        auditoria.registrar(id, null, "COMPETENCIA_FINALIZADA",
                "Se finalizó la competencia");
        return saved;
    }

    @Transactional
    public CompetenciaEntity archivar(Long id) {
        CompetenciaEntity entity = findById(id);

        if (ARCHIVADA.equals(entity.getEstado())) {
            return entity;
        }

        CompetenciaStateMachine.validarTransicion(entity.getEstado(), ARCHIVADA);
        entity.setEstado(ARCHIVADA);
        return competenciaRepo.save(entity);
    }

    private String generateCodigo() {
        String random = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "RTL-" + random;
    }

    /**
     * Crea los snapshots {@code INICIO} de Q1 para cada equipo a partir de los
     * valores iniciales de la competencia. Caller-side helper extraído de
     * {@link #iniciar(Long)} para que flujos que arrancan en {@code EN_CURSO}
     * sin pasar por {@code iniciar} (p. ej. la competencia DEMO con su seed y
     * su {@code reiniciar}) puedan inicializar el estado requerido por el motor.
     *
     * <p>No es idempotente: la UNIQUE {@code (equipo_id, trimestre_id, momento)}
     * fallará si los snapshots ya existen. El caller debe garantizar que se
     * llama una sola vez por Q1.
     */
    public void crearSnapshotsInicialesQ1(CompetenciaEntity comp,
                                          List<EquipoEntity> equipos,
                                          TrimestreEntity q1) {
        for (EquipoEntity equipo : equipos) {
            SnapshotEstadoEntity snap = new SnapshotEstadoEntity();
            snap.setEquipoId(equipo.getId());
            snap.setTrimestreId(q1.getId());
            snap.setMomento("INICIO");
            snap.setCaja(comp.getCajaInicial());
            snap.setDeuda(0);
            snap.setPatrimonioNeto(comp.getCajaInicial() + comp.getValorPlantaInicial());
            snap.setValorPlanta(comp.getValorPlantaInicial());
            snap.setCapacidad(comp.getCapacidadInicial());
            snap.setHeadcount(comp.getHeadcountInicial());
            snap.setSalario(comp.getSalarioInicial());
            snap.setInventario(comp.getInventarioInicial());
            snap.setBrandEquity(new BigDecimal("50.00"));
            snap.setCalidadPercibida(new BigDecimal("50.00"));
            snap.setIdAcumulado(0);
            snap.setPip(new BigDecimal("100.00"));
            snapshotRepo.save(snap);
        }
    }
}
