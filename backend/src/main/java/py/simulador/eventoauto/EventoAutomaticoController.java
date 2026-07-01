package py.simulador.eventoauto;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class EventoAutomaticoController {

    private final EventoAutomaticoAplicadoRepository aplicadoRepo;
    private final EventoAutomaticoReglaRepository reglaRepo;

    public EventoAutomaticoController(EventoAutomaticoAplicadoRepository aplicadoRepo,
                                      EventoAutomaticoReglaRepository reglaRepo) {
        this.aplicadoRepo = aplicadoRepo;
        this.reglaRepo = reglaRepo;
    }

    @GetMapping("/competencias/{id}/eventos-automaticos")
    public ResponseEntity<List<Map<String, Object>>> listarEventosAutomaticos(@PathVariable Long id) {
        List<EventoAutomaticoAplicadoEntity> aplicados = aplicadoRepo.findByCompetenciaId(id);

        List<Map<String, Object>> result = aplicados.stream().map(a -> {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", a.getId());
            dto.put("equipo_id", a.getEquipoId());
            dto.put("trimestre_origen", a.getTrimestreOrigen());
            dto.put("trimestre_efecto_inicio", a.getTrimestreEfectoInicio());
            dto.put("trimestre_efecto_fin", a.getTrimestreEfectoFin());
            dto.put("created_at", a.getCreatedAt());

            reglaRepo.findById(a.getReglaId()).ifPresent(regla -> {
                dto.put("regla_nombre", regla.getNombre());
                dto.put("regla_descripcion", regla.getDescripcion());
                dto.put("efecto_tipo", regla.getEfectoTipo());
                dto.put("efecto_valor", regla.getEfectoValor());
            });

            return dto;
        }).toList();

        return ResponseEntity.ok(result);
    }
}
