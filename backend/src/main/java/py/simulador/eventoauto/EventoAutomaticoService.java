package py.simulador.eventoauto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import py.simulador.decision.DecisionEquipoEntity;
import py.simulador.motor.ResultadoEquipo;
import py.simulador.resultado.SnapshotEstadoEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Evaluates automatic event rules against a team's decisions and results.
 * Triggered events apply to the NEXT quarter, not the current one.
 */
@Service
public class EventoAutomaticoService {

    private static final Logger log = LoggerFactory.getLogger(EventoAutomaticoService.class);

    private final EventoAutomaticoReglaRepository reglaRepo;
    private final EventoAutomaticoAplicadoRepository aplicadoRepo;

    public EventoAutomaticoService(EventoAutomaticoReglaRepository reglaRepo,
                                   EventoAutomaticoAplicadoRepository aplicadoRepo) {
        this.reglaRepo = reglaRepo;
        this.aplicadoRepo = aplicadoRepo;
    }

    /**
     * Evaluates all active rules for a team after their quarter results are calculated.
     * Stores triggered events in evento_automatico_aplicado.
     *
     * @param competenciaId   competition ID
     * @param equipoId        team ID
     * @param trimestreActual current quarter number
     * @param numTrimestres   total quarters in the competition
     * @param decision        team's decision for this quarter
     * @param resultado       calculated result
     * @param snapshot        beginning-of-quarter snapshot
     * @param marketingPromedio average marketing investment across teams
     */
    public void evaluar(Long competenciaId, long equipoId, int trimestreActual, int numTrimestres,
                        DecisionEquipoEntity decision, ResultadoEquipo resultado,
                        SnapshotEstadoEntity snapshot, long marketingPromedio) {

        List<EventoAutomaticoReglaEntity> reglas = reglaRepo.findAllActivos();

        for (EventoAutomaticoReglaEntity regla : reglas) {
            boolean condicionCumplida = evaluarCondicion(regla, decision, resultado, snapshot, marketingPromedio);

            if (condicionCumplida) {
                // Check probability
                double roll = ThreadLocalRandom.current().nextDouble();
                if (roll < regla.getProbabilidad().doubleValue()) {
                    // Effects apply to NEXT quarter
                    int inicioEfecto = trimestreActual + 1;
                    int finEfecto = inicioEfecto + regla.getDuracionTrimestres() - 1;

                    // Don't create events beyond the competition's last quarter
                    if (inicioEfecto > numTrimestres) continue;
                    if (finEfecto > numTrimestres) finEfecto = numTrimestres;

                    EventoAutomaticoAplicadoEntity aplicado = new EventoAutomaticoAplicadoEntity();
                    aplicado.setCompetenciaId(competenciaId);
                    aplicado.setEquipoId(equipoId);
                    aplicado.setReglaId(regla.getId());
                    aplicado.setTrimestreOrigen(trimestreActual);
                    aplicado.setTrimestreEfectoInicio(inicioEfecto);
                    aplicado.setTrimestreEfectoFin(finEfecto);
                    aplicado.setCreatedAt(OffsetDateTime.now());
                    aplicadoRepo.save(aplicado);

                    log.info("Auto-evento '{}' disparado para equipo {} en Q{} (efecto Q{}-Q{})",
                            regla.getNombre(), equipoId, trimestreActual, inicioEfecto, finEfecto);
                }
            }
        }
    }

    /**
     * Gets active auto-events affecting a specific team in the current quarter.
     * Used by the motor to apply effects at the start of processing.
     */
    public List<EventoAutomaticoAplicadoEntity> getActivosParaEquipo(
            Long competenciaId, Long equipoId, int trimestreActual) {
        return aplicadoRepo.findActivosParaEquipo(competenciaId, equipoId, trimestreActual);
    }

    /**
     * Get the rule definition for an applied event.
     */
    public EventoAutomaticoReglaEntity getRegla(Long reglaId) {
        return reglaRepo.findById(reglaId).orElse(null);
    }

    private boolean evaluarCondicion(EventoAutomaticoReglaEntity regla,
                                     DecisionEquipoEntity decision,
                                     ResultadoEquipo resultado,
                                     SnapshotEstadoEntity snapshot,
                                     long marketingPromedio) {
        String tipo = regla.getCondicionTipo();
        BigDecimal umbral = regla.getCondicionUmbral();

        switch (tipo) {
            case "PRODUCCION_ALTA_SIN_AUMENTO" -> {
                // produccion_planificada/capacidad > umbral AND aumento_salarial_pct <= 0
                if (snapshot.getCapacidad() <= 0) return false;
                BigDecimal utilizacion = BigDecimal.valueOf(decision.getProduccionPlanificada())
                        .divide(BigDecimal.valueOf(snapshot.getCapacidad()), 4, RoundingMode.HALF_UP);
                BigDecimal aumento = decision.getAumentoSalarialPct() != null
                        ? decision.getAumentoSalarialPct() : BigDecimal.ZERO;
                return compararOperador(utilizacion, umbral, regla.getCondicionOperador())
                        && aumento.compareTo(BigDecimal.ZERO) <= 0;
            }
            case "PRODUCCION_MUY_ALTA" -> {
                // produccion_planificada/capacidad > umbral
                if (snapshot.getCapacidad() <= 0) return false;
                BigDecimal utilizacion = BigDecimal.valueOf(decision.getProduccionPlanificada())
                        .divide(BigDecimal.valueOf(snapshot.getCapacidad()), 4, RoundingMode.HALF_UP);
                return compararOperador(utilizacion, umbral, regla.getCondicionOperador());
            }
            case "CAPACITACION_Y_AUMENTO" -> {
                // inversion_capacitacion > 0 AND aumento_salarial_pct > umbral
                BigDecimal aumento = decision.getAumentoSalarialPct() != null
                        ? decision.getAumentoSalarialPct() : BigDecimal.ZERO;
                return decision.getInversionCapacitacion() > 0
                        && compararOperador(aumento, umbral, regla.getCondicionOperador());
            }
            case "SALARIO_BAJO" -> {
                // aumento_salarial_pct < umbral (negative threshold)
                BigDecimal aumento = decision.getAumentoSalarialPct() != null
                        ? decision.getAumentoSalarialPct() : BigDecimal.ZERO;
                return compararOperador(aumento, umbral, regla.getCondicionOperador());
            }
            case "MARKETING_ALTO" -> {
                // inversion_marketing > promedio * umbral (umbral = 1.5)
                if (marketingPromedio <= 0) return false;
                BigDecimal ratio = BigDecimal.valueOf(decision.getInversionMarketing())
                        .divide(BigDecimal.valueOf(marketingPromedio), 4, RoundingMode.HALF_UP);
                return compararOperador(ratio, umbral, regla.getCondicionOperador());
            }
            default -> {
                log.warn("Tipo de condicion desconocido: {}", tipo);
                return false;
            }
        }
    }

    private boolean compararOperador(BigDecimal valor, BigDecimal umbral, String operador) {
        int cmp = valor.compareTo(umbral);
        return switch (operador) {
            case ">" -> cmp > 0;
            case "<" -> cmp < 0;
            case ">=" -> cmp >= 0;
            case "<=" -> cmp <= 0;
            case "=" -> cmp == 0;
            default -> false;
        };
    }
}
