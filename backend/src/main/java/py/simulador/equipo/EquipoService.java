package py.simulador.equipo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.api.generated.model.EquipoCreate;
import py.simulador.api.generated.model.EquipoUpdate;
import py.simulador.bot.BotPersonalityAssigner;
import py.simulador.bot.model.Difficulty;
import py.simulador.bot.model.Personality;
import py.simulador.common.BusinessValidationException;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.competencia.CompetenciaStateMachine;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class EquipoService {

    /** Equipo type is immutable after creation: a team is HUMANO or BOT for its full lifecycle. */
    private static final String TIPO_HUMANO = "HUMANO";
    private static final String TIPO_BOT = "BOT";

    private final EquipoRepository equipoRepo;
    private final EquipoMiembroRepository miembroRepo;
    private final CompetenciaRepository competenciaRepo;
    private final BotPersonalityAssigner personalityAssigner;

    public EquipoService(EquipoRepository equipoRepo,
                         EquipoMiembroRepository miembroRepo,
                         CompetenciaRepository competenciaRepo,
                         BotPersonalityAssigner personalityAssigner) {
        this.equipoRepo = equipoRepo;
        this.miembroRepo = miembroRepo;
        this.competenciaRepo = competenciaRepo;
        this.personalityAssigner = personalityAssigner;
    }

    @Transactional(readOnly = true)
    public List<EquipoEntity> findByCompetencia(Long competenciaId) {
        return equipoRepo.findByCompetenciaId(competenciaId);
    }

    @Transactional(readOnly = true)
    public EquipoEntity findById(Long id) {
        return equipoRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Equipo", id));
    }

    @Transactional
    public EquipoEntity create(Long competenciaId, EquipoCreate input) {
        CompetenciaEntity comp = competenciaRepo.findById(competenciaId)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia", competenciaId));

        String estado = comp.getEstado();
        if (!CompetenciaStateMachine.BORRADOR.equals(estado)
                && !CompetenciaStateMachine.ABIERTA_INSCRIPCION.equals(estado)) {
            throw new BusinessValidationException(
                    "Solo se pueden crear equipos en estado BORRADOR o ABIERTA_INSCRIPCION");
        }

        long count = equipoRepo.countByCompetenciaId(competenciaId);
        if (count >= comp.getNumEquiposMax()) {
            throw new BusinessValidationException(
                    "Se alcanzo el maximo de equipos (" + comp.getNumEquiposMax() + ")");
        }

        EquipoEntity entity = new EquipoEntity();
        entity.setCompetenciaId(competenciaId);
        entity.setNombreEmpresa(input.getNombreEmpresa());
        entity.setCodigoColor(input.getCodigoColor());
        entity.setEstado("ACTIVO");
        return equipoRepo.save(entity);
    }

    /**
     * Crea un equipo controlado por bot delegando al overload con override de personalidad
     * en {@code null}, lo que activa la asignacion automatica via {@link BotPersonalityAssigner}.
     */
    @Transactional
    public EquipoEntity crearEquipoBot(Long competenciaId, String nombre, String color, Difficulty dificultad) {
        return crearEquipoBot(competenciaId, nombre, color, dificultad, null);
    }

    /**
     * Crea un equipo controlado por bot. Reusa las validaciones de {@link #create} (competencia
     * en BORRADOR/ABIERTA_INSCRIPCION y limite de equipos). Si {@code personalidadOverride} es
     * no nula se respeta tal cual; en caso contrario se asigna automaticamente una personalidad
     * usando {@link BotPersonalityAssigner} a partir de las personalidades de los bots ya
     * existentes en la competencia.
     */
    @Transactional
    public EquipoEntity crearEquipoBot(Long competenciaId, String nombre, String color,
                                       Difficulty dificultad, Personality personalidadOverride) {
        if (dificultad == null) {
            throw new BusinessValidationException("La dificultad del bot es requerida");
        }

        CompetenciaEntity comp = competenciaRepo.findById(competenciaId)
                .orElseThrow(() -> new ResourceNotFoundException("Competencia", competenciaId));

        String estado = comp.getEstado();
        if (!CompetenciaStateMachine.BORRADOR.equals(estado)
                && !CompetenciaStateMachine.ABIERTA_INSCRIPCION.equals(estado)) {
            throw new BusinessValidationException(
                    "Solo se pueden crear equipos en estado BORRADOR o ABIERTA_INSCRIPCION");
        }

        long count = equipoRepo.countByCompetenciaId(competenciaId);
        if (count >= comp.getNumEquiposMax()) {
            throw new BusinessValidationException(
                    "Se alcanzo el maximo de equipos (" + comp.getNumEquiposMax() + ")");
        }

        Personality finalPersonality;
        if (personalidadOverride != null) {
            finalPersonality = personalidadOverride;
        } else {
            List<EquipoEntity> existingBots = equipoRepo.findByCompetenciaIdAndTipo(competenciaId, TIPO_BOT);
            List<Personality> existingPersonalities = existingBots.stream()
                    .map(EquipoEntity::getPersonalidad)
                    .filter(p -> p != null && !p.isBlank())
                    .map(Personality::valueOf)
                    .toList();
            finalPersonality = personalityAssigner.assignFor(existingPersonalities);
        }

        EquipoEntity entity = new EquipoEntity();
        entity.setCompetenciaId(competenciaId);
        entity.setNombreEmpresa(nombre);
        entity.setCodigoColor(color);
        entity.setEstado("ACTIVO");
        entity.setTipo(TIPO_BOT);
        entity.setDificultad(dificultad.name());
        entity.setPersonalidad(finalPersonality.name());
        return equipoRepo.save(entity);
    }

    @Transactional
    public EquipoEntity update(Long id, EquipoUpdate input) {
        EquipoEntity entity = findById(id);
        if (input.getNombreEmpresa() != null) {
            entity.setNombreEmpresa(input.getNombreEmpresa());
        }
        if (input.getCodigoColor() != null) {
            entity.setCodigoColor(input.getCodigoColor());
        }
        return equipoRepo.save(entity);
    }

    @Transactional(readOnly = true)
    public List<EquipoMiembroEntity> findMiembros(Long equipoId) {
        return miembroRepo.findByEquipoId(equipoId);
    }

    @Transactional
    public EquipoMiembroEntity addMiembro(Long equipoId, Long usuarioId) {
        return addMiembro(equipoId, usuarioId, null, false);
    }

    @Transactional
    public EquipoMiembroEntity addMiembro(Long equipoId, Long usuarioId, Long areaId, boolean esCapitan) {
        EquipoEntity equipo = findById(equipoId);

        // Bot teams are agent-controlled: no human members are ever added.
        if (equipo.esBot()) {
            throw new BusinessValidationException(
                    "No se pueden agregar jugadores a un equipo de tipo BOT");
        }

        // Check user not already in another team of same competition
        List<EquipoEntity> equiposComp = equipoRepo.findByCompetenciaId(equipo.getCompetenciaId());
        for (EquipoEntity otroEquipo : equiposComp) {
            if (!otroEquipo.getId().equals(equipoId)) {
                miembroRepo.findByEquipoIdAndUsuarioId(otroEquipo.getId(), usuarioId)
                        .ifPresent(m -> {
                            throw new BusinessValidationException(
                                    "El jugador ya pertenece al equipo '" + otroEquipo.getNombreEmpresa() + "' en esta competencia");
                        });
            }
        }

        // If setting as captain, unset current captain
        if (esCapitan) {
            List<EquipoMiembroEntity> miembros = miembroRepo.findByEquipoId(equipoId);
            for (EquipoMiembroEntity m : miembros) {
                if (m.isEsCapitan()) {
                    m.setEsCapitan(false);
                    miembroRepo.save(m);
                }
            }
        }

        EquipoMiembroEntity miembro = new EquipoMiembroEntity();
        miembro.setEquipoId(equipoId);
        miembro.setUsuarioId(usuarioId);
        miembro.setAreaId(areaId);
        miembro.setEsCapitan(esCapitan);
        miembro.setJoinedAt(OffsetDateTime.now());
        return miembroRepo.save(miembro);
    }

    @Transactional
    public void removeMiembro(Long equipoId, Long miembroId) {
        EquipoMiembroEntity miembro = miembroRepo.findById(miembroId)
                .orElseThrow(() -> new ResourceNotFoundException("EquipoMiembro", miembroId));
        if (!miembro.getEquipoId().equals(equipoId)) {
            throw new ResourceNotFoundException("EquipoMiembro", "equipoId", equipoId.toString());
        }
        miembroRepo.delete(miembro);
    }

    @Transactional
    public void setCapitan(Long equipoId, Long miembroId) {
        List<EquipoMiembroEntity> miembros = miembroRepo.findByEquipoId(equipoId);

        EquipoMiembroEntity nuevoCapitan = miembros.stream()
                .filter(m -> m.getId().equals(miembroId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("EquipoMiembro", miembroId));

        for (EquipoMiembroEntity m : miembros) {
            if (m.isEsCapitan() && !m.getId().equals(miembroId)) {
                m.setEsCapitan(false);
                miembroRepo.save(m);
            }
        }

        nuevoCapitan.setEsCapitan(true);
        miembroRepo.save(nuevoCapitan);
    }

    /**
     * Asigna capitán aleatorio si el equipo no tiene uno.
     * Retorna true si se asignó un nuevo capitán.
     */
    @Transactional
    public boolean autoAssignCapitanIfMissing(Long equipoId) {
        List<EquipoMiembroEntity> miembros = miembroRepo.findByEquipoId(equipoId);
        boolean tieneCap = miembros.stream().anyMatch(EquipoMiembroEntity::isEsCapitan);
        if (tieneCap || miembros.isEmpty()) return false;

        Collections.shuffle(miembros);
        EquipoMiembroEntity elegido = miembros.get(0);
        elegido.setEsCapitan(true);
        miembroRepo.save(elegido);
        return true;
    }

    /**
     * Actualiza el área de un miembro. Solo el capitán del equipo puede hacerlo.
     */
    @Transactional
    public EquipoMiembroEntity updateMiembroArea(Long equipoId, Long miembroId,
                                                  Long areaId, Long requestingUserId) {
        List<EquipoMiembroEntity> miembros = miembroRepo.findByEquipoId(equipoId);

        boolean esCap = miembros.stream()
                .anyMatch(m -> m.getUsuarioId().equals(requestingUserId) && m.isEsCapitan());
        if (!esCap) {
            throw new BusinessValidationException("Solo el capitan puede asignar areas");
        }

        EquipoMiembroEntity target = miembros.stream()
                .filter(m -> m.getId().equals(miembroId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("EquipoMiembro", miembroId));

        target.setAreaId(areaId);
        return miembroRepo.save(target);
    }
}
