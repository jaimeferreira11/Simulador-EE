package py.simulador.entidad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import py.simulador.H2IntegrationTestBase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proof-of-concept integration test running on H2 in-memory.
 * Verifies the H2 profile boots, the schema loads and a Spring Data JDBC
 * repository can read/write the {@code sim.entidad} table.
 *
 * <p>Run with: {@code ./mvnw test -Dtest=EntidadRepositoryH2Test}
 */
class EntidadRepositoryH2Test extends H2IntegrationTestBase {

    @Autowired
    private EntidadRepository repository;

    @Test
    void seedFromDataSqlIsLoaded() {
        List<EntidadEntity> all = repository.findAllOrdered();
        assertThat(all).isNotEmpty();
        assertThat(all)
                .extracting(EntidadEntity::getNombre)
                .contains("Entidad Demo H2");
    }

    @Test
    void saveAndReloadEntidad() {
        EntidadEntity entidad = new EntidadEntity();
        entidad.setNombre("Universidad H2 Test " + System.nanoTime());
        entidad.setTipo("UNIVERSIDAD");
        entidad.setActiva(true);

        EntidadEntity saved = repository.save(entidad);
        assertThat(saved.getId()).isNotNull();

        EntidadEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getNombre()).isEqualTo(entidad.getNombre());
        assertThat(reloaded.isActiva()).isTrue();
    }

    @Test
    void findAllActivasFiltersInactive() {
        EntidadEntity inactiva = new EntidadEntity();
        inactiva.setNombre("Inactiva " + System.nanoTime());
        inactiva.setTipo("ONG");
        inactiva.setActiva(false);
        repository.save(inactiva);

        List<EntidadEntity> activas = repository.findAllActivas();
        assertThat(activas)
                .extracting(EntidadEntity::isActiva)
                .containsOnly(true);
    }
}
