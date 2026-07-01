package py.simulador.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameEventPublisherTest {

    private WebSocketSessionManager sessionManager;
    private CompetenciaRepository competenciaRepo;
    private ObjectMapper objectMapper;
    private GameEventPublisher publisher;

    @BeforeEach
    void setUp() {
        sessionManager = mock(WebSocketSessionManager.class);
        competenciaRepo = mock(CompetenciaRepository.class);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        publisher = new GameEventPublisher(sessionManager, competenciaRepo, objectMapper);
    }

    @Test
    void sendToUser_invokesSessionManagerWithJsonContainingTipoAndPayload() throws Exception {
        Long usuarioId = 42L;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", 7L);
        payload.put("titulo", "Tu equipo envio decisiones");

        publisher.sendToUser(usuarioId, "notificacion.nueva", payload);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(sessionManager, times(1)).sendToUser(eq(usuarioId), jsonCaptor.capture());

        ObjectNode parsed = (ObjectNode) objectMapper.readTree(jsonCaptor.getValue());
        assertThat(parsed.get("tipo").asText()).isEqualTo("notificacion.nueva");
        assertThat(parsed.has("timestamp")).isTrue();
        assertThat(parsed.get("payload").get("id").asLong()).isEqualTo(7L);
        assertThat(parsed.get("payload").get("titulo").asText())
                .isEqualTo("Tu equipo envio decisiones");
    }

    @Test
    void publish_resolvesCompetenciaCodeAndBroadcasts() throws Exception {
        Long competenciaId = 11L;
        CompetenciaEntity comp = new CompetenciaEntity();
        comp.setId(competenciaId);
        comp.setCodigo("ABC-123");
        when(competenciaRepo.findById(competenciaId)).thenReturn(Optional.of(comp));

        Map<String, Object> payload = Map.of("trimestre_id", 5L);
        publisher.publish(competenciaId, "trimestre.procesado", payload);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(sessionManager).broadcast(eq("ABC-123"), jsonCaptor.capture());

        ObjectNode parsed = (ObjectNode) objectMapper.readTree(jsonCaptor.getValue());
        assertThat(parsed.get("tipo").asText()).isEqualTo("trimestre.procesado");
        assertThat(parsed.get("competencia_id").asLong()).isEqualTo(competenciaId);
        assertThat(parsed.get("payload").get("trimestre_id").asLong()).isEqualTo(5L);
    }

    @Test
    void publish_ignoresUnknownCompetencia() {
        when(competenciaRepo.findById(99L)).thenReturn(Optional.empty());

        publisher.publish(99L, "evento.x", Map.of());

        verify(sessionManager, never()).broadcast(anyString(), anyString());
    }
}
