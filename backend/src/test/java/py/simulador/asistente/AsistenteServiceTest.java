package py.simulador.asistente;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import py.simulador.asistente.dto.OrigenRespuesta;
import py.simulador.asistente.dto.RespuestaAsistente;
import py.simulador.common.ResourceNotFoundException;
import py.simulador.competencia.CompetenciaEntity;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.llm.LlmProperties;

import java.util.List;
import java.util.Optional;

import py.simulador.equipo.EquipoMiembroRepository;
import py.simulador.equipo.EquipoRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsistenteServiceTest {

    @Mock CompetenciaRepository competenciaRepo;
    @Mock AsistenteFaqProvider faqProvider;
    @Mock AsistenteRagProvider ragProvider;
    @Mock AsistenteConsultaLogRepository logRepo;
    @Mock EquipoRepository equipoRepo;
    @Mock EquipoMiembroRepository miembroRepo;

    private CompetenciaEntity competencia(boolean iaHabilitada) {
        CompetenciaEntity c = new CompetenciaEntity();
        c.setId(7L);
        c.setCodigo("ABC123");
        c.setIaHabilitada(iaHabilitada);
        return c;
    }

    private AsistenteService service(String provider) {
        return new AsistenteService(competenciaRepo, faqProvider, ragProvider, logRepo,
                new LlmProperties(provider, null, null), equipoRepo, miembroRepo);
    }

    @Test
    void iaApagada_usaFaqYRegistraOrigen() {
        when(competenciaRepo.findByCodigo("ABC123")).thenReturn(Optional.of(competencia(false)));
        when(equipoRepo.findByCompetenciaId(anyLong())).thenReturn(java.util.List.of());
        when(faqProvider.responder(any())).thenReturn(new RespuestaAsistente(
                "r", List.of(), List.of(), OrigenRespuesta.FAQ));

        RespuestaAsistente r = service("openai").responder("ABC123", 99L, "hola");

        assertThat(r.origen()).isEqualTo(OrigenRespuesta.FAQ);
        verifyNoInteractions(ragProvider);
        ArgumentCaptor<AsistenteConsultaLogEntity> cap =
                ArgumentCaptor.forClass(AsistenteConsultaLogEntity.class);
        verify(logRepo).save(cap.capture());
        assertThat(cap.getValue().getOrigen()).isEqualTo("FAQ");
        assertThat(cap.getValue().isHuboMatch()).isTrue();
    }

    @Test
    void iaEncendidaConProviderReal_usaRag() {
        when(competenciaRepo.findByCodigo("ABC123")).thenReturn(Optional.of(competencia(true)));
        when(equipoRepo.findByCompetenciaId(anyLong())).thenReturn(java.util.List.of());
        when(ragProvider.responder(any())).thenReturn(new RespuestaAsistente(
                "respuesta ia", List.of(), List.of(), OrigenRespuesta.RAG));

        RespuestaAsistente r = service("openai").responder("ABC123", 99L, "hola");

        assertThat(r.origen()).isEqualTo(OrigenRespuesta.RAG);
        verifyNoInteractions(faqProvider);
    }

    @Test
    void iaEncendidaPeroProviderTemplate_usaFaq() {
        when(competenciaRepo.findByCodigo("ABC123")).thenReturn(Optional.of(competencia(true)));
        when(equipoRepo.findByCompetenciaId(anyLong())).thenReturn(java.util.List.of());
        when(faqProvider.responder(any())).thenReturn(new RespuestaAsistente(
                "r", List.of(), List.of(), OrigenRespuesta.FAQ));

        RespuestaAsistente r = service("template").responder("ABC123", 99L, "hola");

        assertThat(r.origen()).isEqualTo(OrigenRespuesta.FAQ);
        verifyNoInteractions(ragProvider);
    }

    @Test
    void competenciaInexistente_lanza404() {
        when(competenciaRepo.findByCodigo("NOPE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service("openai").responder("NOPE", 1L, "x"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resuelveElEquipoDelJugadorYLoPasaAlProvider() {
        when(competenciaRepo.findByCodigo("ABC123")).thenReturn(java.util.Optional.of(competencia(true)));
        py.simulador.equipo.EquipoEntity eq = new py.simulador.equipo.EquipoEntity();
        eq.setId(55L);
        when(equipoRepo.findByCompetenciaId(7L)).thenReturn(java.util.List.of(eq));
        when(miembroRepo.findByEquipoIdAndUsuarioId(55L, 99L))
                .thenReturn(java.util.Optional.of(new py.simulador.equipo.EquipoMiembroEntity()));

        org.mockito.ArgumentCaptor<py.simulador.asistente.dto.AsistenteContexto> cap =
                org.mockito.ArgumentCaptor.forClass(py.simulador.asistente.dto.AsistenteContexto.class);
        when(ragProvider.responder(cap.capture())).thenReturn(new RespuestaAsistente(
                "r", List.of(), List.of(), OrigenRespuesta.RAG));

        service("openai").responder("ABC123", 99L, "¿cómo voy?");

        assertThat(cap.getValue().equipoId()).isEqualTo(55L);
    }
}
