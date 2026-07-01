package py.simulador.asistente;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import py.simulador.asistente.dto.PreguntaRequest;
import py.simulador.asistente.dto.RespuestaAsistente;
import py.simulador.config.SecurityUtils;

@RestController
@Validated
@RequestMapping("/v1/competencias/{codigo}/asistente")
public class AsistenteController {

    private final AsistenteService asistenteService;

    public AsistenteController(AsistenteService asistenteService) {
        this.asistenteService = asistenteService;
    }

    @PostMapping
    public RespuestaAsistente preguntar(@PathVariable String codigo,
                                        @Valid @RequestBody PreguntaRequest body) {
        Long usuarioId = SecurityUtils.getUserId();
        return asistenteService.responder(codigo, usuarioId, body.pregunta());
    }
}
