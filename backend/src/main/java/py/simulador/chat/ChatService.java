package py.simulador.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.simulador.equipo.EquipoEntity;
import py.simulador.equipo.EquipoMiembroEntity;
import py.simulador.equipo.EquipoMiembroRepository;
import py.simulador.equipo.EquipoRepository;
import py.simulador.notificacion.NotificacionService;
import py.simulador.websocket.WebSocketSessionManager;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class ChatService {

    private final ChatMensajeRepository chatRepo;
    private final EquipoMiembroRepository miembroRepo;
    private final EquipoRepository equipoRepo;
    private final NotificacionService notificacionService;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public ChatService(ChatMensajeRepository chatRepo,
                       EquipoMiembroRepository miembroRepo,
                       EquipoRepository equipoRepo,
                       NotificacionService notificacionService,
                       WebSocketSessionManager sessionManager,
                       ObjectMapper objectMapper) {
        this.chatRepo = chatRepo;
        this.miembroRepo = miembroRepo;
        this.equipoRepo = equipoRepo;
        this.notificacionService = notificacionService;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ChatMensajeEntity> listar(Long equipoId, Long competenciaId, int page, int size) {
        return chatRepo.findByEquipoAndCompetencia(equipoId, competenciaId, size, page * size);
    }

    @Transactional(readOnly = true)
    public long contar(Long equipoId, Long competenciaId) {
        return chatRepo.countByEquipoAndCompetencia(equipoId, competenciaId);
    }

    @Transactional
    public ChatMensajeEntity enviar(Long equipoId, Long competenciaId, Long usuarioId, String contenido) {
        ChatMensajeEntity msg = new ChatMensajeEntity();
        msg.setEquipoId(equipoId);
        msg.setCompetenciaId(competenciaId);
        msg.setUsuarioId(usuarioId);
        msg.setContenido(contenido);
        msg.setCreatedAt(OffsetDateTime.now());
        msg = chatRepo.save(msg);
        chatRepo.trimOldMessages(equipoId, competenciaId);
        return msg;
    }

    public void broadcastAndNotify(ChatMensajeEntity msg, String competenciaCodigo, String nombreUsuario) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                "tipo", "chat.mensaje",
                "equipoId", msg.getEquipoId(),
                "mensaje", Map.of(
                    "id", msg.getId(),
                    "usuario_id", msg.getUsuarioId(),
                    "nombre_usuario", nombreUsuario,
                    "contenido", msg.getContenido(),
                    "created_at", msg.getCreatedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                )
            ));
            sessionManager.sendToEquipo(competenciaCodigo, msg.getEquipoId(), json);
        } catch (Exception e) {
            // WS broadcast failure should not fail the operation
        }
        notifyOfflineMembers(msg, nombreUsuario);
    }

    public void validarMiembroEquipo(Long equipoId, Long usuarioId) {
        miembroRepo.findByEquipoIdAndUsuarioId(equipoId, usuarioId)
            .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "No eres miembro de este equipo"));
    }

    private void notifyOfflineMembers(ChatMensajeEntity msg, String nombreUsuario) {
        List<EquipoMiembroEntity> miembros = miembroRepo.findByEquipoId(msg.getEquipoId());
        EquipoEntity equipo = equipoRepo.findById(msg.getEquipoId()).orElse(null);
        String empresaNombre = equipo != null ? equipo.getNombreEmpresa() : "tu equipo";
        String contenidoTruncado = msg.getContenido().length() > 80
            ? msg.getContenido().substring(0, 77) + "..."
            : msg.getContenido();

        for (EquipoMiembroEntity miembro : miembros) {
            if (miembro.getUsuarioId().equals(msg.getUsuarioId())) continue;
            if (sessionManager.isUserOnline(miembro.getUsuarioId())) continue;
            notificacionService.notificarUsuario(
                miembro.getUsuarioId(), msg.getCompetenciaId(),
                "CHAT_EQUIPO", "Nuevo mensaje en " + empresaNombre,
                nombreUsuario + ": " + contenidoTruncado, "INFO");
        }
    }
}
