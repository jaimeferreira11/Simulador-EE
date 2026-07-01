package py.simulador.equipo;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import py.simulador.api.generated.EquiposApi;
import py.simulador.api.generated.model.*;
import py.simulador.auth.RolCache;
import py.simulador.bot.model.Difficulty;
import py.simulador.bot.model.Personality;
import py.simulador.common.BusinessValidationException;
import py.simulador.config.SecurityUtils;
import py.simulador.usuario.UsuarioMapper;
import py.simulador.usuario.UsuarioRepository;

import java.util.List;
import java.util.Map;

@RestController
public class EquipoController implements EquiposApi {

    private final EquipoService service;
    private final EquipoMapper mapper;
    private final UsuarioRepository usuarioRepo;
    private final UsuarioMapper usuarioMapper;
    private final RolCache rolCache;

    public EquipoController(EquipoService service, EquipoMapper mapper,
                            UsuarioRepository usuarioRepo, UsuarioMapper usuarioMapper,
                            RolCache rolCache) {
        this.service = service;
        this.mapper = mapper;
        this.usuarioRepo = usuarioRepo;
        this.usuarioMapper = usuarioMapper;
        this.rolCache = rolCache;
    }

    @Override
    public ResponseEntity<List<Equipo>> competenciasCompetenciaIdEquiposGet(Long competenciaId) {
        var dtos = service.findByCompetencia(competenciaId).stream()
                .map(mapper::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @Override
    public ResponseEntity<Equipo> competenciasCompetenciaIdEquiposPost(Long competenciaId,
                                                                       EquipoCreate input) {
        String tipo = input.getTipo() != null ? input.getTipo().getValue() : "HUMANO";
        EquipoEntity entity;
        if ("BOT".equals(tipo)) {
            EquipoCreate.DificultadEnum difEnum = input.getDificultad() != null
                    && input.getDificultad().isPresent()
                    ? input.getDificultad().get()
                    : null;
            if (difEnum == null) {
                throw new BusinessValidationException(
                        "La dificultad del bot es requerida cuando tipo=BOT");
            }
            Difficulty dif;
            try {
                dif = Difficulty.valueOf(difEnum.getValue());
            } catch (IllegalArgumentException ex) {
                throw new BusinessValidationException(
                        "Dificultad invalida. Valores permitidos: FACIL, MEDIO, DIFICIL");
            }
            Personality personalidadOverride = null;
            String personalidadRaw = input.getPersonalidad() != null
                    && input.getPersonalidad().isPresent()
                    ? input.getPersonalidad().get()
                    : null;
            if (personalidadRaw != null && !personalidadRaw.isBlank()) {
                try {
                    personalidadOverride = Personality.valueOf(personalidadRaw);
                } catch (IllegalArgumentException ex) {
                    throw new BusinessValidationException(
                            "Personalidad invalida. Valores permitidos: COST_LEADER, PREMIUM, BALANCEADO");
                }
            }
            entity = service.crearEquipoBot(competenciaId, input.getNombreEmpresa(),
                    input.getCodigoColor(), dif, personalidadOverride);
        } else {
            entity = service.create(competenciaId, input);
        }
        return ResponseEntity.status(201).body(mapper.toDto(entity));
    }

    @Override
    public ResponseEntity<EquipoDetalle> equiposIdGet(Long id) {
        EquipoEntity entity = service.findById(id);
        List<EquipoMiembroEntity> miembros = service.findMiembros(id);
        EquipoDetalle dto = mapper.toDetalleDto(entity, miembros);
        // Enrich miembros with usuario data
        if (dto.getMiembros() != null) {
            for (EquipoMiembro m : dto.getMiembros()) {
                if (m.getUsuarioId() != null) {
                    usuarioRepo.findById(m.getUsuarioId()).ifPresent(u ->
                        m.setUsuario(usuarioMapper.toDto(u, rolCache))
                    );
                }
            }
        }
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<Equipo> equiposIdPatch(Long id, EquipoUpdate input) {
        return ResponseEntity.ok(mapper.toDto(service.update(id, input)));
    }

    @Override
    public ResponseEntity<List<EquipoMiembro>> equiposIdMiembrosGet(Long id) {
        var dtos = service.findMiembros(id).stream()
                .map(m -> {
                    EquipoMiembro dto = mapper.toMiembroDto(m);
                    usuarioRepo.findById(m.getUsuarioId()).ifPresent(u ->
                        dto.setUsuario(usuarioMapper.toDto(u, rolCache))
                    );
                    return dto;
                })
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @Override
    public ResponseEntity<EquipoMiembro> equiposIdMiembrosPost(Long id,
                                                                 EquiposIdMiembrosPostRequest request) {
        Long areaId = request.getAreaId() != null && request.getAreaId().isPresent()
                ? request.getAreaId().get() : null;
        EquipoMiembroEntity entity = service.addMiembro(id, request.getUsuarioId(),
                areaId, Boolean.TRUE.equals(request.getEsCapitan()));
        EquipoMiembro dto = mapper.toMiembroDto(entity);
        usuarioRepo.findById(entity.getUsuarioId()).ifPresent(u ->
            dto.setUsuario(usuarioMapper.toDto(u, rolCache))
        );
        return ResponseEntity.status(201).body(dto);
    }

    @Override
    public ResponseEntity<Void> equiposIdMiembrosMiembroIdDelete(Long id, Long miembroId) {
        service.removeMiembro(id, miembroId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> equiposIdMiembrosMiembroIdCapitanPut(Long id, Long miembroId) {
        service.setCapitan(id, miembroId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/equipos/{equipoId}/miembros/{miembroId}/area")
    public ResponseEntity<EquipoMiembro> updateMiembroArea(
            @PathVariable Long equipoId,
            @PathVariable Long miembroId,
            @RequestBody Map<String, Long> body) {
        Long userId = SecurityUtils.getUserId();
        Long areaId = body.get("area_id");
        EquipoMiembroEntity entity = service.updateMiembroArea(equipoId, miembroId, areaId, userId);
        EquipoMiembro dto = mapper.toMiembroDto(entity);
        usuarioRepo.findById(entity.getUsuarioId()).ifPresent(u ->
            dto.setUsuario(usuarioMapper.toDto(u, rolCache))
        );
        return ResponseEntity.ok(dto);
    }
}
