package py.simulador.asistente;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecuperadorContextoTest {

    @Mock AsistenteBaseConocimiento base;

    private List<Fragmento> corpus() {
        return List.of(
                new Fragmento("Glosario de términos", "Definiciones varias."),
                new Fragmento("Resumen de límites importantes", "Trimestres 4 a 8."),
                new Fragmento("Paso 7 — Esperar el cierre",
                        "El moderador cierra el trimestre y se calcula el ranking."),
                new Fragmento("Consejos finales", "No descuides la caja."));
    }

    @Test
    void recupera_lasSeccionesConMasSolapamiento() {
        when(base.fragmentos()).thenReturn(corpus());
        RecuperadorContexto rec = new RecuperadorContexto(base);

        String ctx = rec.recuperar("cuando el moderador cierra el trimestre que pasa", 4);

        assertThat(ctx).contains("Paso 7 — Esperar el cierre");
        assertThat(ctx).contains("se calcula el ranking");
    }

    @Test
    void sinCoincidencias_devuelveElDefault() {
        when(base.fragmentos()).thenReturn(corpus());
        RecuperadorContexto rec = new RecuperadorContexto(base);

        String ctx = rec.recuperar("zzz qqq xxx", 4);

        assertThat(ctx).contains("Glosario de términos");
        assertThat(ctx).contains("Resumen de límites importantes");
        assertThat(ctx).doesNotContain("Consejos finales");
    }

    @Test
    void respetaElTopK() {
        when(base.fragmentos()).thenReturn(corpus());
        RecuperadorContexto rec = new RecuperadorContexto(base);

        // "moderador" y "caja" matchean 2 fragmentos distintos; con k=1 solo entra el de mayor score
        String ctx = rec.recuperar("moderador cierra trimestre ranking", 1);

        assertThat(ctx).contains("Paso 7 — Esperar el cierre");
        assertThat(ctx).doesNotContain("Consejos finales");
    }
}
