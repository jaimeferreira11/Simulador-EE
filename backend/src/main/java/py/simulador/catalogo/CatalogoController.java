package py.simulador.catalogo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import py.simulador.api.generated.CatlogosApi;
import py.simulador.api.generated.model.*;

import java.util.List;

@RestController
public class CatalogoController implements CatlogosApi {

    private final CatalogoService service;
    private final CatalogoMapper mapper;

    public CatalogoController(CatalogoService service, CatalogoMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<Rubro>> catalogosRubrosGet(Boolean activo) {
        List<Rubro> dtos = service.findRubros(activo).stream()
                .map(mapper::toRubroDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @Override
    public ResponseEntity<Rubro> catalogosRubrosPost(RubroCreate rubroCreate) {
        RubroEntity entity = service.createRubro(rubroCreate);
        return ResponseEntity.status(201).body(mapper.toRubroDto(entity));
    }

    @Override
    public ResponseEntity<RubroConParametros> catalogosRubrosIdGet(Long id) {
        RubroEntity rubro = service.findRubroById(id);
        List<ParametroRubroEntity> params = service.findParametrosRubro(id);
        RubroConParametros dto = new RubroConParametros();
        dto.setId(rubro.getId());
        dto.setCodigo(rubro.getCodigo());
        dto.setNombre(rubro.getNombre());
        dto.setDescripcion(rubro.getDescripcion());
        dto.setActivo(rubro.isActivo());
        dto.setParametros(params.stream().map(mapper::toParametroRubroDto).toList());
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<List<ParametroMacro>> catalogosParametrosMacroGet(Boolean activo) {
        List<ParametroMacro> dtos = service.findParametrosMacro(activo).stream()
                .map(mapper::toParametroMacroDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @Override
    public ResponseEntity<ParametroMacro> catalogosParametrosMacroPost(ParametroMacroCreate input) {
        ParametroMacroEntity entity = service.createParametroMacro(input);
        return ResponseEntity.status(201).body(mapper.toParametroMacroDto(entity));
    }

    @Override
    public ResponseEntity<ParametroMacro> catalogosParametrosMacroIdGet(Long id) {
        return ResponseEntity.ok(mapper.toParametroMacroDto(service.findParametroMacroById(id)));
    }

    @Override
    public ResponseEntity<List<ParametroRubro>> catalogosParametrosRubroGet(Long rubroId) {
        List<ParametroRubro> dtos = service.findParametrosRubro(rubroId).stream()
                .map(mapper::toParametroRubroDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/catalogos/areas")
    public ResponseEntity<List<AreaDecisionResponse>> areasGet() {
        var dtos = service.findAreasActivas().stream()
                .map(a -> new AreaDecisionResponse(a.getId(), a.getCodigo(), a.getNombre(), a.getDescripcion()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    public record AreaDecisionResponse(Long id, String codigo, String nombre, String descripcion) {}

    @Override
    public ResponseEntity<List<EventoCatalogo>> catalogosEventosGet(Long rubroId) {
        List<EventoCatalogo> dtos = service.findEventosCatalogo(rubroId).stream()
                .map(mapper::toEventoCatalogoDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @Override
    public ResponseEntity<EventoCatalogo> catalogosEventosPost(EventoCatalogoCreate input) {
        EventoCatalogoEntity entity = service.createEventoCatalogo(input);
        return ResponseEntity.status(201).body(mapper.toEventoCatalogoDto(entity));
    }
}
