package py.simulador.catalogo;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.api.generated.model.*;
import py.simulador.common.ResourceNotFoundException;

import java.util.List;

@Service
public class CatalogoService {

    private final RubroRepository rubroRepo;
    private final ParametroMacroRepository macroRepo;
    private final ParametroRubroRepository paramRubroRepo;
    private final EventoCatalogoRepository eventoCatalogoRepo;
    private final AreaDecisionRepository areaDecisionRepo;

    public CatalogoService(RubroRepository rubroRepo,
                           ParametroMacroRepository macroRepo,
                           ParametroRubroRepository paramRubroRepo,
                           EventoCatalogoRepository eventoCatalogoRepo,
                           AreaDecisionRepository areaDecisionRepo) {
        this.rubroRepo = rubroRepo;
        this.macroRepo = macroRepo;
        this.paramRubroRepo = paramRubroRepo;
        this.eventoCatalogoRepo = eventoCatalogoRepo;
        this.areaDecisionRepo = areaDecisionRepo;
    }

    // --- Rubros ---

    @Transactional(readOnly = true)
    public List<RubroEntity> findRubros(Boolean activo) {
        if (Boolean.TRUE.equals(activo)) {
            return rubroRepo.findAllActivos();
        }
        return (List<RubroEntity>) rubroRepo.findAll();
    }

    @Transactional(readOnly = true)
    public RubroEntity findRubroById(Long id) {
        return rubroRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rubro", id));
    }

    @Transactional
    public RubroEntity createRubro(RubroCreate input) {
        RubroEntity entity = new RubroEntity();
        entity.setCodigo(input.getCodigo());
        entity.setNombre(input.getNombre());
        entity.setDescripcion(input.getDescripcion());
        entity.setActivo(true);
        return rubroRepo.save(entity);
    }

    // --- Parametros Macro ---

    @Transactional(readOnly = true)
    public List<ParametroMacroEntity> findParametrosMacro(Boolean activo) {
        if (Boolean.TRUE.equals(activo)) {
            return macroRepo.findAllActivos();
        }
        return (List<ParametroMacroEntity>) macroRepo.findAll();
    }

    @Transactional(readOnly = true)
    public ParametroMacroEntity findParametroMacroById(Long id) {
        return macroRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ParametroMacro", id));
    }

    @Transactional
    public ParametroMacroEntity createParametroMacro(ParametroMacroCreate input) {
        ParametroMacroEntity entity = new ParametroMacroEntity();
        entity.setNombreSet(input.getNombreSet());
        entity.setActivo(true);
        // All numeric fields would be set from input — abbreviated here
        return macroRepo.save(entity);
    }

    // --- Parametros Rubro ---

    @Transactional(readOnly = true)
    public List<ParametroRubroEntity> findParametrosRubro(Long rubroId) {
        return paramRubroRepo.findByRubroIdActivos(rubroId);
    }

    // --- Areas Decision ---

    @Transactional(readOnly = true)
    public List<AreaDecisionEntity> findAreasActivas() {
        return areaDecisionRepo.findAllActivas();
    }

    // --- Eventos Catalogo ---

    @Transactional(readOnly = true)
    public List<EventoCatalogoEntity> findEventosCatalogo(Long rubroId) {
        if (rubroId != null) {
            return eventoCatalogoRepo.findActivosByRubro(rubroId);
        }
        return eventoCatalogoRepo.findAllActivos();
    }

    @Transactional
    public EventoCatalogoEntity createEventoCatalogo(EventoCatalogoCreate input) {
        EventoCatalogoEntity entity = new EventoCatalogoEntity();
        entity.setCodigo(input.getCodigo());
        entity.setNombre(input.getNombre());
        entity.setDescripcion(input.getDescripcion());
        entity.setSeveridad(input.getSeveridad().getValue());
        entity.setTipoEfecto(input.getTipoEfecto().getValue());
        entity.setMagnitudDefault(input.getMagnitudDefault());
        entity.setDuracionQ(input.getDuracionQ().shortValue());
        entity.setActivo(true);
        return eventoCatalogoRepo.save(entity);
    }
}
