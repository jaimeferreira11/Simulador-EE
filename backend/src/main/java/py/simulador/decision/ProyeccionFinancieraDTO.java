package py.simulador.decision;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO para la proyeccion financiera what-if.
 * Calcula estimaciones basadas en las decisiones parciales del jugador.
 */
public record ProyeccionFinancieraDTO(
        @JsonProperty("caja_proyectada") long cajaProyectada,
        @JsonProperty("ingresos_estimados") long ingresosEstimados,
        @JsonProperty("costos_variables_est") long costosVariablesEst,
        @JsonProperty("costos_fijos_est") long costosFijosEst,
        @JsonProperty("costo_laboral_est") long costoLaboralEst,
        @JsonProperty("intereses_est") long interesesEst,
        @JsonProperty("inversion_total") long inversionTotal,
        @JsonProperty("utilizacion_planta") double utilizacionPlanta,
        @JsonProperty("semaforo_caja") String semaforoCaja,
        List<AdvertenciaDTO> advertencias
) {

    public record AdvertenciaDTO(
            String tipo,
            String campo,
            String mensaje
    ) {}
}
