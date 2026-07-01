package py.simulador.escenario;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import py.simulador.catalogo.EventoCatalogoEntity;
import py.simulador.catalogo.EventoCatalogoRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/escenarios")
public class EscenarioController {

    private final EscenarioPredefinidoRepository escenarioRepo;
    private final EscenarioEventoRepository escenarioEventoRepo;
    private final EventoCatalogoRepository eventoCatalogoRepo;

    public EscenarioController(EscenarioPredefinidoRepository escenarioRepo,
                               EscenarioEventoRepository escenarioEventoRepo,
                               EventoCatalogoRepository eventoCatalogoRepo) {
        this.escenarioRepo = escenarioRepo;
        this.escenarioEventoRepo = escenarioEventoRepo;
        this.eventoCatalogoRepo = eventoCatalogoRepo;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(@RequestParam(required = false) Long rubro_id) {
        List<EscenarioPredefinidoEntity> escenarios = rubro_id != null
                ? escenarioRepo.findAllActivosByRubro(rubro_id)
                : escenarioRepo.findAllActivos();

        List<Map<String, Object>> result = escenarios.stream().map(esc -> {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", esc.getId());
            dto.put("nombre", esc.getNombre());
            dto.put("descripcion", esc.getDescripcion());
            dto.put("num_trimestres", esc.getNumTrimestres());
            dto.put("caja_inicial", esc.getCajaInicial());
            dto.put("capacidad_inicial", esc.getCapacidadInicial());
            dto.put("headcount_inicial", esc.getHeadcountInicial());
            dto.put("salario_inicial", esc.getSalarioInicial());
            dto.put("inventario_inicial", esc.getInventarioInicial());
            dto.put("valor_planta_inicial", esc.getValorPlantaInicial());
            dto.put("dificultad", esc.getDificultad());
            dto.put("rubro_id", esc.getRubroId());

            List<EscenarioEventoEntity> eventos = escenarioEventoRepo.findByEscenarioId(esc.getId());
            dto.put("eventos", eventos.stream().map(ev -> {
                Map<String, Object> evDto = new LinkedHashMap<>();
                evDto.put("trimestre_numero", ev.getTrimestreNumero());
                evDto.put("evento_catalogo_id", ev.getEventoCatalogoId());
                eventoCatalogoRepo.findById(ev.getEventoCatalogoId()).ifPresent(cat -> {
                    evDto.put("evento_codigo", cat.getCodigo());
                    evDto.put("evento_nombre", cat.getNombre());
                    evDto.put("severidad", cat.getSeveridad());
                });
                return evDto;
            }).toList());

            return dto;
        }).toList();

        return ResponseEntity.ok(result);
    }
}
