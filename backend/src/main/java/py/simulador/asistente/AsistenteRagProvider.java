package py.simulador.asistente;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import py.simulador.asistente.dto.AsistenteContexto;
import py.simulador.asistente.dto.OrigenRespuesta;
import py.simulador.asistente.dto.RespuestaAsistente;
import py.simulador.asistente.dto.RespuestaAsistente.Fuente;
import py.simulador.llm.LlmCompletion;
import py.simulador.llm.LlmProvider;

import java.util.List;

/**
 * Provider de Fase 2: responde con el LLM configurado, grounded en la documentación
 * (context-stuffing). Ante cualquier error del LLM, cae al FAQ determinístico.
 */
@Component
public class AsistenteRagProvider implements AsistenteProvider {

    private static final Logger log = LoggerFactory.getLogger(AsistenteRagProvider.class);

    private final LlmProvider llmProvider;
    private final RecuperadorContexto recuperador;
    private final AsistenteFaqProvider faqProvider;
    private final GuardrailFilter guardrail;
    private final AsistenteFaqRepository faqRepo;
    private final ResumenEstadoEquipoService resumenEstado;

    public AsistenteRagProvider(LlmProvider llmProvider,
                                RecuperadorContexto recuperador,
                                AsistenteFaqProvider faqProvider,
                                GuardrailFilter guardrail,
                                AsistenteFaqRepository faqRepo,
                                ResumenEstadoEquipoService resumenEstado) {
        this.llmProvider = llmProvider;
        this.recuperador = recuperador;
        this.faqProvider = faqProvider;
        this.guardrail = guardrail;
        this.faqRepo = faqRepo;
        this.resumenEstado = resumenEstado;
    }

    @Override
    public RespuestaAsistente responder(AsistenteContexto ctx) {
        try {
            String recuperado = recuperador.recuperar(ctx.pregunta(), RecuperadorContexto.K);
            String contexto = recuperado;
            if (ctx.equipoId() != null) {
                String estado = resumenEstado.resumir(ctx.equipoId(), ctx.competenciaId());
                if (estado != null && !estado.isBlank()) {
                    contexto = "ESTADO ACTUAL DE TU EMPRESA:\n" + estado + "\n\n" + recuperado;
                }
            }
            String prompt = PromptAsistente.construir(contexto, ctx.pregunta());
            LlmCompletion completion = llmProvider.completarPrompt(prompt);
            String texto = completion != null ? guardrail.revisar(completion.content()) : null;
            if (texto == null || texto.isBlank()) {
                return faqProvider.responder(ctx);
            }
            List<String> relacionadas = faqRepo.findByActivaTrueOrderByOrdenAsc().stream()
                    .limit(3).map(AsistenteFaqEntity::getPregunta).toList();
            return new RespuestaAsistente(
                    texto,
                    List.of(new Fuente("Manual del jugador", "Guía paso a paso para Jugador")),
                    relacionadas,
                    OrigenRespuesta.RAG);
        } catch (Exception e) {
            log.warn("Asistente RAG falló ({}). Fallback a FAQ.", e.toString());
            return faqProvider.responder(ctx);
        }
    }
}
