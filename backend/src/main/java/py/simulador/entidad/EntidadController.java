package py.simulador.entidad;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/entidades")
public class EntidadController {

    private final EntidadRepository repo;

    public EntidadController(EntidadRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public ResponseEntity<List<EntidadEntity>> listar() {
        return ResponseEntity.ok(repo.findAllActivas());
    }
}
