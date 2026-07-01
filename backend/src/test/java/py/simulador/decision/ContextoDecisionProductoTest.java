package py.simulador.decision;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import py.simulador.catalogo.AreaDecisionRepository;
import py.simulador.catalogo.EventoCatalogoRepository;
import py.simulador.catalogo.MateriaPrimaRubroEntity;
import py.simulador.catalogo.MateriaPrimaRubroRepository;
import py.simulador.catalogo.ParametroMacroRepository;
import py.simulador.catalogo.ParametroMacroTrimestreRepository;
import py.simulador.catalogo.ParametroRubroRepository;
import py.simulador.catalogo.ParametroRubroTrimestreRepository;
import py.simulador.catalogo.RubroEntity;
import py.simulador.catalogo.RubroRepository;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.equipo.EquipoMiembroRepository;
import py.simulador.equipo.EquipoRepository;
import py.simulador.evento.EventoCompetenciaRepository;
import py.simulador.resultado.RankingTrimestreRepository;
import py.simulador.resultado.SnapshotEstadoRepository;
import py.simulador.trimestre.TrimestreRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ContextoDecisionService#buildProducto}, the read-only
 * mapping that exposes the rubro's product + Bill of Materials (BOM) in the
 * player's decision-context payload.
 *
 * <p>Pure Mockito (no database / Testcontainers), so it runs in any sandbox.
 */
@ExtendWith(MockitoExtension.class)
class ContextoDecisionProductoTest {

    private static final long RUBRO_ID = 7L;

    @Mock TrimestreRepository trimestreRepo;
    @Mock CompetenciaRepository competenciaRepo;
    @Mock EquipoRepository equipoRepo;
    @Mock EquipoMiembroRepository miembroRepo;
    @Mock SnapshotEstadoRepository snapshotRepo;
    @Mock RankingTrimestreRepository rankingRepo;
    @Mock EventoCompetenciaRepository eventoCompRepo;
    @Mock EventoCatalogoRepository eventoCatalogoRepo;
    @Mock DecisionEquipoRepository decisionRepo;
    @Mock AreaDecisionRepository areaRepo;
    @Mock ParametroRubroRepository parametroRubroRepo;
    @Mock ParametroMacroRepository parametroMacroRepo;
    @Mock ParametroMacroTrimestreRepository macroTrimestreRepo;
    @Mock ParametroRubroTrimestreRepository rubroTrimestreRepo;
    @Mock RubroRepository rubroRepo;
    @Mock MateriaPrimaRubroRepository materiaPrimaRepo;

    private ContextoDecisionService service;

    @BeforeEach
    void setUp() {
        service = new ContextoDecisionService(
                trimestreRepo, competenciaRepo, equipoRepo, miembroRepo,
                snapshotRepo, rankingRepo, eventoCompRepo, eventoCatalogoRepo,
                decisionRepo, areaRepo, parametroRubroRepo, parametroMacroRepo,
                macroTrimestreRepo, rubroTrimestreRepo, rubroRepo, materiaPrimaRepo);
    }

    @Test
    void buildProducto_conProductoYBom_mapeaCamposYSumaCostoBase() {
        RubroEntity rubro = new RubroEntity();
        rubro.setId(RUBRO_ID);
        rubro.setProductoNombre("Bebida embotellada 500 ml");
        rubro.setProductoDescripcion("Produccion y venta de bebidas en tiendas de conveniencia.");
        rubro.setUnidadMedida("unidad");
        when(rubroRepo.findById(RUBRO_ID)).thenReturn(Optional.of(rubro));

        // BOM ya devuelto en orden por la query (ORDER BY orden ASC).
        when(materiaPrimaRepo.findByRubroIdOrderByOrdenAsc(RUBRO_ID)).thenReturn(List.of(
                mp("Concentrado de fruta", 2710),
                mp("Azucar", 1806),
                mp("Envase PET", 2323),
                mp("Tapa", 645),
                mp("Etiqueta", 516)
        ));

        ContextoDecisionDTO.ProductoRubroDTO producto = service.buildProducto(RUBRO_ID);

        assertThat(producto).isNotNull();
        assertThat(producto.nombre()).isEqualTo("Bebida embotellada 500 ml");
        assertThat(producto.descripcion())
                .isEqualTo("Produccion y venta de bebidas en tiendas de conveniencia.");
        assertThat(producto.unidadMedida()).isEqualTo("unidad");

        // costo base = suma exacta del BOM (= parametro_rubro.costo_unit_mp = 8000)
        assertThat(producto.costoBaseUnitario()).isEqualTo(8000L);

        // materias primas en orden de presentacion
        assertThat(producto.materiasPrimas())
                .extracting(ContextoDecisionDTO.MateriaPrimaDTO::nombre)
                .containsExactly("Concentrado de fruta", "Azucar", "Envase PET", "Tapa", "Etiqueta");
        assertThat(producto.materiasPrimas())
                .extracting(ContextoDecisionDTO.MateriaPrimaDTO::costoUnitario)
                .containsExactly(2710L, 1806L, 2323L, 645L, 516L);
    }

    @Test
    void buildProducto_sinProducto_devuelveNull() {
        RubroEntity rubro = new RubroEntity();
        rubro.setId(RUBRO_ID);
        rubro.setProductoNombre(null); // rubro sin producto definido
        when(rubroRepo.findById(RUBRO_ID)).thenReturn(Optional.of(rubro));

        assertThat(service.buildProducto(RUBRO_ID)).isNull();
    }

    private static MateriaPrimaRubroEntity mp(String nombre, long costo) {
        MateriaPrimaRubroEntity e = new MateriaPrimaRubroEntity();
        e.setNombre(nombre);
        e.setCostoUnitario(costo);
        return e;
    }
}
