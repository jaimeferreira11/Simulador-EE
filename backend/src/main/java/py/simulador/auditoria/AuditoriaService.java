package py.simulador.auditoria;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AuditoriaService {

    private final AuditoriaEventoRepository repo;

    public AuditoriaService(AuditoriaEventoRepository repo) {
        this.repo = repo;
    }

    public void registrar(Long competenciaId, Long usuarioId, String tipoAccion, String descripcion) {
        repo.insertar(competenciaId, usuarioId, tipoAccion, descripcion, OffsetDateTime.now());
    }
}
