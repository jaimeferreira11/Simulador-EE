package py.simulador.admin;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.auth.RolCache;
import py.simulador.catalogo.*;
import py.simulador.common.BusinessValidationException;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.entidad.EntidadEntity;
import py.simulador.entidad.EntidadRepository;
import py.simulador.usuario.UsuarioEntity;
import py.simulador.usuario.UsuarioRepository;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

@Service
public class AdminService {

    private final UsuarioRepository usuarioRepo;
    private final RubroRepository rubroRepo;
    private final ParametroMacroRepository macroRepo;
    private final ParametroMacroTrimestreRepository macroTriRepo;
    private final ParametroRubroRepository paramRubroRepo;
    private final ParametroRubroTrimestreRepository rubroTriRepo;
    private final EventoCatalogoRepository eventoRepo;
    private final EntidadRepository entidadRepo;
    private final RolCache rolCache;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UsuarioRepository usuarioRepo,
                        RubroRepository rubroRepo,
                        ParametroMacroRepository macroRepo,
                        ParametroMacroTrimestreRepository macroTriRepo,
                        ParametroRubroRepository paramRubroRepo,
                        ParametroRubroTrimestreRepository rubroTriRepo,
                        EventoCatalogoRepository eventoRepo,
                        EntidadRepository entidadRepo,
                        RolCache rolCache,
                        PasswordEncoder passwordEncoder) {
        this.usuarioRepo = usuarioRepo;
        this.rubroRepo = rubroRepo;
        this.macroRepo = macroRepo;
        this.macroTriRepo = macroTriRepo;
        this.paramRubroRepo = paramRubroRepo;
        this.rubroTriRepo = rubroTriRepo;
        this.eventoRepo = eventoRepo;
        this.entidadRepo = entidadRepo;
        this.rolCache = rolCache;
        this.passwordEncoder = passwordEncoder;
    }

    // =====================================================================
    // USUARIOS
    // =====================================================================

    @Transactional(readOnly = true)
    public PagedResponse<AdminDtos.UsuarioRow> listUsuarios(int page, int size,
                                                             String q, String rol, Boolean activo) {
        List<AdminDtos.UsuarioRow> all = toList(usuarioRepo.findAll()).stream()
                .filter(u -> activo == null || u.isActivo() == activo)
                .filter(u -> rol == null || rol.equalsIgnoreCase(rolCache.getCodigo(u.getRolUsuarioId())))
                .filter(u -> matchesSearch(q, u.getEmail(), u.getNombreCompleto()))
                .sorted(Comparator.comparing(UsuarioEntity::getNombreCompleto))
                .map(u -> new AdminDtos.UsuarioRow(
                        u.getId(), u.getEmail(), u.getNombreCompleto(),
                        rolCache.getCodigo(u.getRolUsuarioId()), u.isActivo(), u.getCreatedAt()))
                .toList();
        return PagedResponse.of(all, page, size);
    }

    @Transactional(readOnly = true)
    public AdminDtos.UsuarioDetail getUsuario(Long id) {
        UsuarioEntity u = findUsuario(id);
        return toUsuarioDetail(u);
    }

    @Transactional
    public AdminDtos.UsuarioDetail createUsuario(AdminDtos.UsuarioCreateRequest req) {
        usuarioRepo.findByEmail(req.email()).ifPresent(e -> {
            throw new BusinessValidationException("El email ya esta registrado");
        });
        Long rolId = resolveRolId(req.rolCodigo());
        UsuarioEntity u = new UsuarioEntity();
        u.setEmail(req.email());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setNombreCompleto(req.nombreCompleto());
        u.setRolUsuarioId(rolId);
        u.setActivo(true);
        u.setEmailVerificado(false);
        return toUsuarioDetail(usuarioRepo.save(u));
    }

    @Transactional
    public AdminDtos.UsuarioDetail updateUsuario(Long id, AdminDtos.UsuarioUpdateRequest req) {
        UsuarioEntity u = findUsuario(id);
        if (req.nombreCompleto() != null) u.setNombreCompleto(req.nombreCompleto());
        if (req.password() != null) u.setPasswordHash(passwordEncoder.encode(req.password()));
        if (req.rolCodigo() != null) u.setRolUsuarioId(resolveRolId(req.rolCodigo()));
        return toUsuarioDetail(usuarioRepo.save(u));
    }

    @Transactional
    public void toggleUsuarioActivo(Long id, boolean activo) {
        UsuarioEntity u = findUsuario(id);
        u.setActivo(activo);
        usuarioRepo.save(u);
    }

    // =====================================================================
    // RUBROS
    // =====================================================================

    @Transactional(readOnly = true)
    public PagedResponse<AdminDtos.RubroRow> listRubros(int page, int size,
                                                         String q, Boolean activo) {
        List<AdminDtos.RubroRow> all = toList(rubroRepo.findAll()).stream()
                .filter(r -> activo == null || r.isActivo() == activo)
                .filter(r -> matchesSearch(q, r.getCodigo(), r.getNombre()))
                .sorted(Comparator.comparing(RubroEntity::getNombre))
                .map(r -> new AdminDtos.RubroRow(
                        r.getId(), r.getCodigo(), r.getNombre(), r.isActivo(), r.getCreatedAt()))
                .toList();
        return PagedResponse.of(all, page, size);
    }

    @Transactional(readOnly = true)
    public AdminDtos.RubroDetail getRubro(Long id) {
        RubroEntity r = findRubro(id);
        return new AdminDtos.RubroDetail(
                r.getId(), r.getCodigo(), r.getNombre(),
                r.getDescripcion(), r.isActivo(), r.getCreatedAt());
    }

    @Transactional
    public AdminDtos.RubroDetail createRubro(AdminDtos.RubroRequest req) {
        rubroRepo.findByCodigo(req.codigo()).ifPresent(e -> {
            throw new BusinessValidationException("El codigo de rubro ya existe: " + req.codigo());
        });
        RubroEntity r = new RubroEntity();
        r.setCodigo(req.codigo());
        r.setNombre(req.nombre());
        r.setDescripcion(req.descripcion());
        r.setActivo(true);
        r = rubroRepo.save(r);
        return getRubro(r.getId());
    }

    @Transactional
    public AdminDtos.RubroDetail updateRubro(Long id, AdminDtos.RubroRequest req) {
        RubroEntity r = findRubro(id);
        r.setCodigo(req.codigo());
        r.setNombre(req.nombre());
        r.setDescripcion(req.descripcion());
        rubroRepo.save(r);
        return getRubro(id);
    }

    @Transactional
    public void toggleRubroActivo(Long id, boolean activo) {
        RubroEntity r = findRubro(id);
        r.setActivo(activo);
        rubroRepo.save(r);
    }

    // =====================================================================
    // PARAMETROS MACRO
    // =====================================================================

    @Transactional(readOnly = true)
    public PagedResponse<AdminDtos.ParamMacroRow> listParamMacro(int page, int size,
                                                                   String q, Boolean activo) {
        List<AdminDtos.ParamMacroRow> all = toList(macroRepo.findAll()).stream()
                .filter(m -> activo == null || m.isActivo() == activo)
                .filter(m -> matchesSearch(q, m.getNombreSet()))
                .sorted(Comparator.comparing(ParametroMacroEntity::getNombreSet))
                .map(m -> new AdminDtos.ParamMacroRow(
                        m.getId(), m.getNombreSet(), m.getVigenteDesde(),
                        m.isActivo(), m.getCreatedAt()))
                .toList();
        return PagedResponse.of(all, page, size);
    }

    @Transactional(readOnly = true)
    public AdminDtos.ParamMacroDetail getParamMacro(Long id) {
        ParametroMacroEntity m = findMacro(id);
        return new AdminDtos.ParamMacroDetail(
                m.getId(), m.getNombreSet(), m.getVigenteDesde(),
                m.getSalarioMinimoQ1(), m.getSalarioMinimoQ4(),
                m.getIpsPatronal(), m.getIpsTrabajador(),
                m.getAguinaldoFactor(), m.getTasaIre(), m.getIvaGeneral(),
                m.isActivo(), m.getCreatedAt());
    }

    @Transactional
    public AdminDtos.ParamMacroDetail createParamMacro(AdminDtos.ParamMacroRequest req) {
        ParametroMacroEntity m = new ParametroMacroEntity();
        applyMacroFields(m, req);
        m.setActivo(true);
        m = macroRepo.save(m);
        return getParamMacro(m.getId());
    }

    @Transactional
    public AdminDtos.ParamMacroDetail updateParamMacro(Long id, AdminDtos.ParamMacroRequest req) {
        ParametroMacroEntity m = findMacro(id);
        applyMacroFields(m, req);
        macroRepo.save(m);
        return getParamMacro(id);
    }

    @Transactional
    public void toggleParamMacroActivo(Long id, boolean activo) {
        ParametroMacroEntity m = findMacro(id);
        m.setActivo(activo);
        macroRepo.save(m);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.MacroTrimestreDto> getParamMacroTrimestres(Long macroId) {
        findMacro(macroId); // validate exists
        return macroTriRepo.findByMacroId(macroId).stream()
                .map(t -> new AdminDtos.MacroTrimestreDto(
                        t.getId(), t.getTrimestre(), t.getInflacionTrim(),
                        t.getTipoCambio(), t.getTpmAnual()))
                .toList();
    }

    @Transactional
    public List<AdminDtos.MacroTrimestreDto> replaceParamMacroTrimestres(
            Long macroId, List<AdminDtos.MacroTrimestreDto> trimestres) {
        findMacro(macroId); // validate exists
        // Delete existing and re-insert
        List<ParametroMacroTrimestreEntity> existing = macroTriRepo.findByMacroId(macroId);
        macroTriRepo.deleteAll(existing);
        for (AdminDtos.MacroTrimestreDto dto : trimestres) {
            ParametroMacroTrimestreEntity t = new ParametroMacroTrimestreEntity();
            t.setMacroId(macroId);
            t.setTrimestre(dto.trimestre());
            t.setInflacionTrim(dto.inflacionTrim());
            t.setTipoCambio(dto.tipoCambio());
            t.setTpmAnual(dto.tpmAnual());
            macroTriRepo.save(t);
        }
        return getParamMacroTrimestres(macroId);
    }

    // =====================================================================
    // PARAMETROS RUBRO
    // =====================================================================

    @Transactional(readOnly = true)
    public PagedResponse<AdminDtos.ParamRubroRow> listParamRubro(int page, int size,
                                                                   String q, Long rubroId, Boolean activo) {
        List<AdminDtos.ParamRubroRow> all = toList(paramRubroRepo.findAll()).stream()
                .filter(p -> activo == null || p.isActivo() == activo)
                .filter(p -> rubroId == null || rubroId.equals(p.getRubroId()))
                .filter(p -> matchesSearch(q, p.getCodigo()))
                .sorted(Comparator.comparing(ParametroRubroEntity::getCodigo))
                .map(p -> {
                    String rubroNombre = rubroRepo.findById(p.getRubroId())
                            .map(RubroEntity::getNombre).orElse("");
                    return new AdminDtos.ParamRubroRow(
                            p.getId(), p.getCodigo(), p.getRubroId(), rubroNombre,
                            p.getDemandaBaseTrim(), p.getPrecioReferencia(),
                            p.isActivo(), p.getCreatedAt());
                })
                .toList();
        return PagedResponse.of(all, page, size);
    }

    @Transactional(readOnly = true)
    public AdminDtos.ParamRubroDetail getParamRubro(Long id) {
        ParametroRubroEntity p = findParamRubro(id);
        String rubroNombre = rubroRepo.findById(p.getRubroId())
                .map(RubroEntity::getNombre).orElse("");
        return new AdminDtos.ParamRubroDetail(
                p.getId(), p.getCodigo(), p.getRubroId(), rubroNombre,
                p.getDemandaBaseTrim(), p.getPrecioReferencia(),
                p.getElasticidadPrecio(), p.getElasticidadMarketing(), p.getElasticidadCalidad(),
                p.getPesoPrecio(), p.getPesoMarketing(), p.getPesoCalidad(), p.getPesoMarca(),
                p.getCostoUnitMp(), p.getPctMpImportada(),
                p.getCostosFijosTrim(), p.getDepreciacionTrim(), p.getCostoExpansionCapacidad(),
                p.getSalarioPromedioSector(), p.getProductividadEmpleado(),
                p.getBrandEquityInicial(), p.getDecaimientoBe(), p.getSpreadTasa(),
                p.isActivo(), p.getCreatedAt());
    }

    @Transactional
    public AdminDtos.ParamRubroDetail createParamRubro(AdminDtos.ParamRubroRequest req) {
        findRubro(req.rubroId()); // validate rubro exists
        ParametroRubroEntity p = new ParametroRubroEntity();
        applyParamRubroFields(p, req);
        p.setActivo(true);
        p = paramRubroRepo.save(p);
        return getParamRubro(p.getId());
    }

    @Transactional
    public AdminDtos.ParamRubroDetail updateParamRubro(Long id, AdminDtos.ParamRubroRequest req) {
        ParametroRubroEntity p = findParamRubro(id);
        findRubro(req.rubroId()); // validate rubro exists
        applyParamRubroFields(p, req);
        paramRubroRepo.save(p);
        return getParamRubro(id);
    }

    @Transactional
    public void toggleParamRubroActivo(Long id, boolean activo) {
        ParametroRubroEntity p = findParamRubro(id);
        p.setActivo(activo);
        paramRubroRepo.save(p);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.RubroTrimestreDto> getParamRubroTrimestres(Long paramRubroId) {
        findParamRubro(paramRubroId);
        return rubroTriRepo.findByRubroParamId(paramRubroId).stream()
                .map(t -> new AdminDtos.RubroTrimestreDto(
                        t.getId(), t.getTrimestre(), t.getEstacionalidad()))
                .toList();
    }

    @Transactional
    public List<AdminDtos.RubroTrimestreDto> replaceParamRubroTrimestres(
            Long paramRubroId, List<AdminDtos.RubroTrimestreDto> trimestres) {
        findParamRubro(paramRubroId);
        List<ParametroRubroTrimestreEntity> existing = rubroTriRepo.findByRubroParamId(paramRubroId);
        rubroTriRepo.deleteAll(existing);
        for (AdminDtos.RubroTrimestreDto dto : trimestres) {
            ParametroRubroTrimestreEntity t = new ParametroRubroTrimestreEntity();
            t.setRubroParamId(paramRubroId);
            t.setTrimestre(dto.trimestre());
            t.setEstacionalidad(dto.estacionalidad());
            rubroTriRepo.save(t);
        }
        return getParamRubroTrimestres(paramRubroId);
    }

    // =====================================================================
    // EVENTOS CATALOGO
    // =====================================================================

    @Transactional(readOnly = true)
    public PagedResponse<AdminDtos.EventoRow> listEventos(int page, int size,
                                                            String q, Long rubroId,
                                                            String severidad, Boolean activo) {
        List<AdminDtos.EventoRow> all = toList(eventoRepo.findAll()).stream()
                .filter(e -> activo == null || e.isActivo() == activo)
                .filter(e -> rubroId == null || rubroId.equals(e.getRubroId()))
                .filter(e -> severidad == null || severidad.equalsIgnoreCase(e.getSeveridad()))
                .filter(e -> matchesSearch(q, e.getCodigo(), e.getNombre()))
                .sorted(Comparator.comparing(EventoCatalogoEntity::getNombre))
                .map(e -> {
                    String rubroNombre = e.getRubroId() != null
                            ? rubroRepo.findById(e.getRubroId()).map(RubroEntity::getNombre).orElse("")
                            : "Global";
                    return new AdminDtos.EventoRow(
                            e.getId(), e.getCodigo(), e.getNombre(), e.getSeveridad(),
                            e.getTipoEfecto(), e.getMagnitudDefault(), e.getDuracionQ(),
                            e.getRubroId(), rubroNombre, e.isActivo());
                })
                .toList();
        return PagedResponse.of(all, page, size);
    }

    @Transactional(readOnly = true)
    public AdminDtos.EventoDetail getEvento(Long id) {
        EventoCatalogoEntity e = findEvento(id);
        String rubroNombre = e.getRubroId() != null
                ? rubroRepo.findById(e.getRubroId()).map(RubroEntity::getNombre).orElse("")
                : "Global";
        return new AdminDtos.EventoDetail(
                e.getId(), e.getCodigo(), e.getNombre(), e.getDescripcion(),
                e.getSeveridad(), e.getTipoEfecto(),
                e.getMagnitudDefault(), e.getDuracionQ(),
                e.isRequiereAnuncioPrevio(),
                e.getOverridePesoPrecio(), e.getOverridePesoMarketing(),
                e.getOverridePesoCalidad(), e.getOverridePesoMarca(),
                e.getRubroId(), rubroNombre, e.isActivo());
    }

    @Transactional
    public AdminDtos.EventoDetail createEvento(AdminDtos.EventoRequest req) {
        eventoRepo.findByCodigo(req.codigo()).ifPresent(e -> {
            throw new BusinessValidationException("El codigo de evento ya existe: " + req.codigo());
        });
        EventoCatalogoEntity e = new EventoCatalogoEntity();
        applyEventoFields(e, req);
        e.setActivo(true);
        e = eventoRepo.save(e);
        return getEvento(e.getId());
    }

    @Transactional
    public AdminDtos.EventoDetail updateEvento(Long id, AdminDtos.EventoRequest req) {
        EventoCatalogoEntity e = findEvento(id);
        applyEventoFields(e, req);
        eventoRepo.save(e);
        return getEvento(id);
    }

    @Transactional
    public void toggleEventoActivo(Long id, boolean activo) {
        EventoCatalogoEntity e = findEvento(id);
        e.setActivo(activo);
        eventoRepo.save(e);
    }

    // =====================================================================
    // ENTIDADES
    // =====================================================================

    @Transactional(readOnly = true)
    public PagedResponse<AdminDtos.EntidadRow> listEntidades(int page, int size,
                                                               String q, Boolean activa) {
        List<AdminDtos.EntidadRow> all = toList(entidadRepo.findAll()).stream()
                .filter(e -> activa == null || e.isActiva() == activa)
                .filter(e -> matchesSearch(q, e.getNombre(), e.getTipo()))
                .sorted(Comparator.comparing(EntidadEntity::getNombre))
                .map(e -> new AdminDtos.EntidadRow(
                        e.getId(), e.getNombre(), e.getTipo(),
                        e.getContactoEmail(), e.isActiva(), e.getCreatedAt()))
                .toList();
        return PagedResponse.of(all, page, size);
    }

    @Transactional(readOnly = true)
    public AdminDtos.EntidadDetail getEntidad(Long id) {
        EntidadEntity e = findEntidad(id);
        return new AdminDtos.EntidadDetail(
                e.getId(), e.getNombre(), e.getTipo(), e.getDescripcion(),
                e.getContactoNombre(), e.getContactoEmail(),
                e.isActiva(), e.getCreatedAt());
    }

    @Transactional
    public AdminDtos.EntidadDetail createEntidad(AdminDtos.EntidadRequest req) {
        EntidadEntity e = new EntidadEntity();
        applyEntidadFields(e, req);
        e.setActiva(true);
        e = entidadRepo.save(e);
        return getEntidad(e.getId());
    }

    @Transactional
    public AdminDtos.EntidadDetail updateEntidad(Long id, AdminDtos.EntidadRequest req) {
        EntidadEntity e = findEntidad(id);
        applyEntidadFields(e, req);
        entidadRepo.save(e);
        return getEntidad(id);
    }

    @Transactional
    public void toggleEntidadActiva(Long id, boolean activa) {
        EntidadEntity e = findEntidad(id);
        e.setActiva(activa);
        entidadRepo.save(e);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private UsuarioEntity findUsuario(Long id) {
        return usuarioRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", id));
    }

    private RubroEntity findRubro(Long id) {
        return rubroRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rubro", id));
    }

    private ParametroMacroEntity findMacro(Long id) {
        return macroRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ParametroMacro", id));
    }

    private ParametroRubroEntity findParamRubro(Long id) {
        return paramRubroRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ParametroRubro", id));
    }

    private EventoCatalogoEntity findEvento(Long id) {
        return eventoRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EventoCatalogo", id));
    }

    private EntidadEntity findEntidad(Long id) {
        return entidadRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entidad", id));
    }

    private Long resolveRolId(String rolCodigo) {
        Long id = rolCache.getId(rolCodigo);
        if (id == null) throw new BusinessValidationException("Rol desconocido: " + rolCodigo);
        return id;
    }

    private AdminDtos.UsuarioDetail toUsuarioDetail(UsuarioEntity u) {
        return new AdminDtos.UsuarioDetail(
                u.getId(), u.getEmail(), u.getNombreCompleto(),
                rolCache.getCodigo(u.getRolUsuarioId()), u.isActivo(), u.isEmailVerificado(),
                u.getCreatedAt(), u.getLastLoginAt());
    }

    private void applyMacroFields(ParametroMacroEntity m, AdminDtos.ParamMacroRequest req) {
        m.setNombreSet(req.nombreSet());
        m.setVigenteDesde(req.vigenteDesde());
        m.setSalarioMinimoQ1(req.salarioMinimoQ1());
        m.setSalarioMinimoQ4(req.salarioMinimoQ4());
        m.setIpsPatronal(req.ipsPatronal());
        m.setIpsTrabajador(req.ipsTrabajador());
        m.setAguinaldoFactor(req.aguinaldoFactor());
        m.setTasaIre(req.tasaIre());
        m.setIvaGeneral(req.ivaGeneral());
    }

    private void applyParamRubroFields(ParametroRubroEntity p, AdminDtos.ParamRubroRequest req) {
        p.setCodigo(req.codigo());
        p.setRubroId(req.rubroId());
        p.setDemandaBaseTrim(req.demandaBaseTrim());
        p.setPrecioReferencia(req.precioReferencia());
        p.setElasticidadPrecio(req.elasticidadPrecio());
        p.setElasticidadMarketing(req.elasticidadMarketing());
        p.setElasticidadCalidad(req.elasticidadCalidad());
        p.setPesoPrecio(req.pesoPrecio());
        p.setPesoMarketing(req.pesoMarketing());
        p.setPesoCalidad(req.pesoCalidad());
        p.setPesoMarca(req.pesoMarca());
        p.setCostoUnitMp(req.costoUnitMp());
        p.setPctMpImportada(req.pctMpImportada());
        p.setCostosFijosTrim(req.costosFijosTrim());
        p.setDepreciacionTrim(req.depreciacionTrim());
        p.setCostoExpansionCapacidad(req.costoExpansionCapacidad());
        p.setSalarioPromedioSector(req.salarioPromedioSector());
        p.setProductividadEmpleado(req.productividadEmpleado());
        p.setBrandEquityInicial(req.brandEquityInicial());
        p.setDecaimientoBe(req.decaimientoBe());
        p.setSpreadTasa(req.spreadTasa());
    }

    private void applyEventoFields(EventoCatalogoEntity e, AdminDtos.EventoRequest req) {
        e.setCodigo(req.codigo());
        e.setNombre(req.nombre());
        e.setDescripcion(req.descripcion());
        e.setSeveridad(req.severidad());
        e.setTipoEfecto(req.tipoEfecto());
        e.setMagnitudDefault(req.magnitudDefault());
        e.setDuracionQ(req.duracionQ());
        e.setRequiereAnuncioPrevio(req.requiereAnuncioPrevio());
        e.setOverridePesoPrecio(req.overridePesoPrecio());
        e.setOverridePesoMarketing(req.overridePesoMarketing());
        e.setOverridePesoCalidad(req.overridePesoCalidad());
        e.setOverridePesoMarca(req.overridePesoMarca());
        e.setRubroId(req.rubroId());
    }

    private void applyEntidadFields(EntidadEntity e, AdminDtos.EntidadRequest req) {
        e.setNombre(req.nombre());
        e.setTipo(req.tipo());
        e.setDescripcion(req.descripcion());
        e.setContactoNombre(req.contactoNombre());
        e.setContactoEmail(req.contactoEmail());
    }

    private boolean matchesSearch(String q, String... fields) {
        if (q == null || q.isBlank()) return true;
        String term = q.toLowerCase();
        for (String f : fields) {
            if (f != null && f.toLowerCase().contains(term)) return true;
        }
        return false;
    }

    private <T> List<T> toList(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }
}
