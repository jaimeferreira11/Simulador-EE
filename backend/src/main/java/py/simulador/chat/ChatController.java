package py.simulador.chat;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import py.simulador.competencia.CompetenciaRepository;
import py.simulador.config.SecurityUtils;
import py.simulador.usuario.UsuarioEntity;
import py.simulador.usuario.UsuarioRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/equipos/{equipoId}/chat")
public class ChatController {

    private final ChatService chatService;
    private final UsuarioRepository usuarioRepo;
    private final CompetenciaRepository competenciaRepo;

    public ChatController(ChatService chatService,
                          UsuarioRepository usuarioRepo,
                          CompetenciaRepository competenciaRepo) {
        this.chatService = chatService;
        this.usuarioRepo = usuarioRepo;
        this.competenciaRepo = competenciaRepo;
    }

    @GetMapping
    public Map<String, Object> listar(
            @PathVariable Long equipoId,
            @RequestParam("competencia_id") Long competenciaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        Long userId = SecurityUtils.getUserId();
        chatService.validarMiembroEquipo(equipoId, userId);

        List<ChatMensajeEntity> mensajes = chatService.listar(equipoId, competenciaId, page, size);
        long total = chatService.contar(equipoId, competenciaId);
        int totalPages = (int) Math.ceil((double) total / size);

        List<Map<String, Object>> content = mensajes.stream().map(m -> {
            String nombre = usuarioRepo.findById(m.getUsuarioId())
                .map(UsuarioEntity::getNombreCompleto)
                .orElse("Desconocido");
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("id", m.getId());
            dto.put("usuario_id", m.getUsuarioId());
            dto.put("nombre_usuario", nombre);
            dto.put("contenido", m.getContenido());
            dto.put("created_at", m.getCreatedAt().toString());
            return dto;
        }).toList();

        return Map.of(
            "content", content,
            "page", page,
            "size", size,
            "total_elements", total,
            "total_pages", totalPages
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> enviar(
            @PathVariable Long equipoId,
            @RequestBody Map<String, Object> body) {

        Long userId = SecurityUtils.getUserId();

        Long competenciaId = ((Number) body.get("competencia_id")).longValue();
        String contenido = (String) body.get("contenido");

        if (contenido == null || contenido.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El contenido no puede estar vacio");
        }
        if (contenido.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El contenido no puede superar 1000 caracteres");
        }

        chatService.validarMiembroEquipo(equipoId, userId);

        UsuarioEntity usuario = usuarioRepo.findById(userId).orElseThrow();

        // Get competencia codigo for WS broadcast
        String competenciaCodigo = competenciaRepo.findById(competenciaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Competencia no encontrada"))
            .getCodigo();

        ChatMensajeEntity msg = chatService.enviar(equipoId, competenciaId, userId, contenido.trim());
        chatService.broadcastAndNotify(msg, competenciaCodigo, usuario.getNombreCompleto());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", msg.getId());
        response.put("usuario_id", msg.getUsuarioId());
        response.put("nombre_usuario", usuario.getNombreCompleto());
        response.put("contenido", msg.getContenido());
        response.put("created_at", msg.getCreatedAt().toString());
        return response;
    }
}
