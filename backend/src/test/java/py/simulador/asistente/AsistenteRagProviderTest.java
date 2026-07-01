package py.simulador.asistente;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import py.simulador.asistente.dto.AsistenteContexto;
import py.simulador.asistente.dto.OrigenRespuesta;
import py.simulador.asistente.dto.RespuestaAsistente;
import py.simulador.llm.CoachingContext;
import py.simulador.llm.EventoContext;
import py.simulador.llm.LlmCompletion;
import py.simulador.llm.LlmProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenteRagProviderTest {

    @Mock RecuperadorContexto recuperador;
    @Mock AsistenteFaqProvider faqProvider;
    @Mock AsistenteFaqRepository faqRepo;
    @Mock ResumenEstadoEquipoService resumenEstado;

    private final GuardrailFilter guardrail = new GuardrailFilter();

    /** LlmProvider stub que devuelve un texto fijo. */
    private LlmProvider stubLlm(String respuesta) {
        return new LlmProvider() {
            public String generarNarrativa(EventoContext ctx) { return ""; }
            public String generarCoaching(CoachingContext ctx) { return ""; }
            public LlmCompletion completarPrompt(String prompt) { return LlmCompletion.of(respuesta); }
        };
    }

    private LlmProvider stubLlmQueFalla() {
        return new LlmProvider() {
            public String generarNarrativa(EventoContext ctx) { return ""; }
            public String generarCoaching(CoachingContext ctx) { return ""; }
            public LlmCompletion completarPrompt(String prompt) {
                throw new RuntimeException("API caída");
            }
        };
    }

    private AsistenteContexto ctxSinEquipo() {
        return new AsistenteContexto("¿cómo se ordena el ranking?", 1L, null);
    }

    private AsistenteContexto ctxConEquipo() {
        return new AsistenteContexto("¿cómo voy?", 1L, 7L);
    }

    @Test
    void exito_devuelveOrigenRagConElTextoDelLlm() {
        when(recuperador.recuperar(any(), anyInt())).thenReturn("DOC");
        AsistenteFaqEntity faq = new AsistenteFaqEntity();
        faq.setPregunta("¿Qué es el PIP?");
        when(faqRepo.findByActivaTrueOrderByOrdenAsc()).thenReturn(List.of(faq));

        AsistenteRagProvider provider = new AsistenteRagProvider(
                stubLlm("El ranking se ordena por utilidad acumulada."),
                recuperador, faqProvider, guardrail, faqRepo, resumenEstado);

        RespuestaAsistente r = provider.responder(ctxSinEquipo());

        assertThat(r.origen()).isEqualTo(OrigenRespuesta.RAG);
        assertThat(r.texto()).isEqualTo("El ranking se ordena por utilidad acumulada.");
        assertThat(r.fuentes()).isNotEmpty();
        assertThat(r.relacionadas()).contains("¿Qué es el PIP?");
    }

    @Test
    void siElLlmFalla_caeAlProviderFaq() {
        when(recuperador.recuperar(any(), anyInt())).thenReturn("DOC");
        RespuestaAsistente fallback = new RespuestaAsistente(
                "respuesta faq", List.of(), List.of(), OrigenRespuesta.FAQ);
        when(faqProvider.responder(any())).thenReturn(fallback);

        AsistenteRagProvider provider = new AsistenteRagProvider(
                stubLlmQueFalla(), recuperador, faqProvider, guardrail, faqRepo, resumenEstado);

        RespuestaAsistente r = provider.responder(ctxSinEquipo());

        assertThat(r.origen()).isEqualTo(OrigenRespuesta.FAQ);
        assertThat(r.texto()).isEqualTo("respuesta faq");
    }

    @Test
    void conEquipo_incluyeElEstadoEnElContextoDelLlm() {
        when(recuperador.recuperar(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn("DOC");
        when(resumenEstado.resumir(7L, 1L)).thenReturn("posición 3, caja Gs 100");
        // capturamos el prompt que recibe el LLM via un stub que lo guarda
        final String[] promptCapturado = {null};
        py.simulador.llm.LlmProvider capturador = new py.simulador.llm.LlmProvider() {
            public String generarNarrativa(py.simulador.llm.EventoContext c) { return ""; }
            public String generarCoaching(py.simulador.llm.CoachingContext c) { return ""; }
            public py.simulador.llm.LlmCompletion completarPrompt(String prompt) {
                promptCapturado[0] = prompt;
                return py.simulador.llm.LlmCompletion.of("ok");
            }
        };
        AsistenteRagProvider provider = new AsistenteRagProvider(
                capturador, recuperador, faqProvider, guardrail, faqRepo, resumenEstado);

        provider.responder(ctxConEquipo());

        assertThat(promptCapturado[0]).contains("ESTADO ACTUAL DE TU EMPRESA");
        assertThat(promptCapturado[0]).contains("posición 3, caja Gs 100");
    }
}
