package py.simulador.asistente;

import org.springframework.stereotype.Component;
import py.simulador.catalogo.EventoCatalogoRepository;
import py.simulador.evento.EventoCompetenciaEntity;
import py.simulador.evento.EventoCompetenciaRepository;
import py.simulador.resultado.RankingTrimestreEntity;
import py.simulador.resultado.RankingTrimestreRepository;
import py.simulador.resultado.ResultadoCalculoRepository;
import py.simulador.trimestre.TrimestreEntity;
import py.simulador.trimestre.TrimestreRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Arma un resumen de SOLO LECTURA del estado del equipo del jugador (estado actual + último
 *  resultado) para que el asistente lo use como contexto. No incluye las decisiones crudas. */
@Component
public class ResumenEstadoEquipoService {

    private final RankingTrimestreRepository rankingRepo;
    private final ResultadoCalculoRepository resultadoRepo;
    private final TrimestreRepository trimestreRepo;
    private final EventoCompetenciaRepository eventoCompRepo;
    private final EventoCatalogoRepository eventoCatalogoRepo;

    public ResumenEstadoEquipoService(RankingTrimestreRepository rankingRepo,
                                      ResultadoCalculoRepository resultadoRepo,
                                      TrimestreRepository trimestreRepo,
                                      EventoCompetenciaRepository eventoCompRepo,
                                      EventoCatalogoRepository eventoCatalogoRepo) {
        this.rankingRepo = rankingRepo;
        this.resultadoRepo = resultadoRepo;
        this.trimestreRepo = trimestreRepo;
        this.eventoCompRepo = eventoCompRepo;
        this.eventoCatalogoRepo = eventoCatalogoRepo;
    }

    public String resumir(Long equipoId, Long competenciaId) {
        List<RankingTrimestreEntity> rangos = rankingRepo.findByCompetenciaId(competenciaId).stream()
                .filter(x -> equipoId.equals(x.getEquipoId()))
                .toList();
        if (rangos.isEmpty()) {
            return "Tu empresa aún no tiene resultados procesados.";
        }

        // Número de trimestre por id (robusto ante ids no monótonos): se elige el de mayor número.
        Map<Long, Integer> numeroPorTrimestre = new HashMap<>();
        for (TrimestreEntity t : trimestreRepo.findByCompetenciaId(competenciaId)) {
            numeroPorTrimestre.put(t.getId(), (int) t.getNumero());
        }
        RankingTrimestreEntity r = rangos.stream()
                .max(Comparator.comparingInt(
                        (RankingTrimestreEntity x) -> numeroPorTrimestre.getOrDefault(x.getTrimestreId(), 0)))
                .orElseThrow();
        int q = numeroPorTrimestre.getOrDefault(r.getTrimestreId(), 0);

        StringBuilder sb = new StringBuilder();
        sb.append("Tras el cierre del trimestre Q").append(q)
          .append(": posición ").append(r.getPosicion()).append("°")
          .append(", market share ").append(pct(r.getShareActual()))
          .append(", caja Gs ").append(r.getCajaActual())
          .append(", utilidad acumulada Gs ").append(r.getUtilidadAcumulada())
          .append(", PIP acumulado ").append(r.getPipAcumulado().setScale(2, RoundingMode.HALF_UP))
          .append(".\n");

        resultadoRepo.findByEquipoIdAndTrimestreId(equipoId, r.getTrimestreId()).ifPresent(res ->
                sb.append("Resultado de Q").append(q)
                  .append(": ventas ").append(res.getVentasUnidades()).append(" uds")
                  .append(", ingresos Gs ").append(res.getIngresos())
                  .append(", costos Gs ").append(res.getCostosOperativosTotal())
                  .append(", utilidad neta Gs ").append(res.getUtilidadNeta())
                  .append(", market share ").append(pct(res.getShare()))
                  .append(".\n"));

        List<EventoCompetenciaEntity> activos =
                eventoCompRepo.findActivosParaTrimestre(competenciaId, r.getTrimestreId());
        if (!activos.isEmpty()) {
            List<Long> catalogoIds = activos.stream()
                    .map(EventoCompetenciaEntity::getEventoCatalogoId).toList();
            Map<Long, String> nombrePorCatalogo = new HashMap<>();
            eventoCatalogoRepo.findAllById(catalogoIds)
                    .forEach(c -> nombrePorCatalogo.put(c.getId(), c.getNombre()));
            List<String> eventos = catalogoIds.stream()
                    .map(nombrePorCatalogo::get).filter(Objects::nonNull).toList();
            if (!eventos.isEmpty()) {
                sb.append("Eventos activos: ").append(String.join(", ", eventos)).append(".\n");
            }
        }

        return sb.toString().strip();
    }

    private static String pct(BigDecimal share) {
        if (share == null) return "0%";
        return String.format("%.1f%%", share.doubleValue() * 100);
    }
}
