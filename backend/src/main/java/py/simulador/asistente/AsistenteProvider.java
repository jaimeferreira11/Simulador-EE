package py.simulador.asistente;

import py.simulador.asistente.dto.AsistenteContexto;
import py.simulador.asistente.dto.RespuestaAsistente;

public interface AsistenteProvider {
    RespuestaAsistente responder(AsistenteContexto ctx);
}
