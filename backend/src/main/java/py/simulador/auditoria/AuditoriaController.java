package py.simulador.auditoria;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import py.simulador.api.generated.AuditoraApi;
import py.simulador.api.generated.model.AuditoriaDecision;
import py.simulador.api.generated.model.AuditoriaEvento;
import py.simulador.api.generated.model.PagedAuditoriaEventos;
import py.simulador.decision.DecisionCampoLogEntity;
import py.simulador.decision.DecisionService;

import java.util.List;

@RestController
public class AuditoriaController implements AuditoraApi {

    private final AuditoriaEventoRepository eventoRepo;
    private final DecisionService decisionService;
    private final AuditoriaMapper mapper;

    public AuditoriaController(AuditoriaEventoRepository eventoRepo,
                               DecisionService decisionService,
                               AuditoriaMapper mapper) {
        this.eventoRepo = eventoRepo;
        this.decisionService = decisionService;
        this.mapper = mapper;
    }

    /**
     * Bitacora general de la competencia (login, cambios de estado, eventos disparados, etc.)
     * Paginación manual sobre la lista completa (Spring Data JDBC no soporta Pageable nativo).
     */
    @Override
    public ResponseEntity<PagedAuditoriaEventos> competenciasCompetenciaIdAuditoriaGet(
            Long competenciaId, Integer page, Integer size, String tipoAccion) {

        int pageNum = page != null ? page : 0;
        int pageSize = size != null ? size : 20;

        List<AuditoriaEventoEntity> todos = (tipoAccion != null && !tipoAccion.isBlank())
                ? eventoRepo.findByCompetenciaIdAndTipoAccion(competenciaId, tipoAccion)
                : eventoRepo.findByCompetenciaId(competenciaId);

        int total = todos.size();
        int from = Math.min(pageNum * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<AuditoriaEvento> content = todos.subList(from, to).stream()
                .map(mapper::toEventoDto)
                .toList();

        PagedAuditoriaEventos response = new PagedAuditoriaEventos();
        response.setContent(content);
        response.setPage(pageNum);
        response.setSize(pageSize);
        response.setTotalElements((long) total);
        response.setTotalPages((total + pageSize - 1) / pageSize);

        return ResponseEntity.ok(response);
    }

    /**
     * Historial de cambios campo a campo de una decisión.
     * Usa decision_campo_log (cambios de valor por campo), no auditoria_decision (cambios de estado).
     */
    @Override
    public ResponseEntity<List<AuditoriaDecision>> decisionesIdHistorialGet(Long id) {
        List<DecisionCampoLogEntity> logs = decisionService.findCampoLog(id);
        List<AuditoriaDecision> dtos = logs.stream()
                .map(mapper::toCampoLogDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }
}
