package py.simulador.decision;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import py.simulador.api.generated.model.DecisionInput;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que las constraints declaradas en {@code DecisionInput}
 * (generadas a partir de la spec OpenAPI) rechacen entradas inválidas.
 *
 * <p>Aplica al endpoint manual {@code POST /equipos/{id}/trimestres/{id}/decision/proyeccion},
 * que ahora usa {@code @Valid @RequestBody DecisionInput} para que estas constraints
 * sean evaluadas y respondan 422 vía GlobalExceptionHandler.
 */
class DecisionInputValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) factory.close();
    }

    @Test
    void precioVentaNegativo_esRechazado() {
        DecisionInput input = new DecisionInput(-100L);

        Set<ConstraintViolation<DecisionInput>> violaciones = validator.validate(input);

        assertThat(violaciones)
                .as("precioVenta < 1 debe violar @Min(1L)")
                .anyMatch(v -> v.getPropertyPath().toString().equals("precioVenta"));
    }

    @Test
    void precioVentaCero_esRechazado() {
        DecisionInput input = new DecisionInput(0L);

        Set<ConstraintViolation<DecisionInput>> violaciones = validator.validate(input);

        assertThat(violaciones)
                .as("precioVenta = 0 debe violar @Min(1L)")
                .anyMatch(v -> v.getPropertyPath().toString().equals("precioVenta"));
    }

    @Test
    void produccionNegativa_esRechazada() {
        DecisionInput input = new DecisionInput(25_000L);
        input.setProduccionPlanificada(-50L);

        Set<ConstraintViolation<DecisionInput>> violaciones = validator.validate(input);

        assertThat(violaciones)
                .as("produccionPlanificada < 0 debe violar @Min(0L)")
                .anyMatch(v -> v.getPropertyPath().toString().equals("produccionPlanificada"));
    }

    @Test
    void inputValido_pasaValidacion() {
        DecisionInput input = new DecisionInput(25_000L);
        input.setProduccionPlanificada(1000L);
        input.setInversionMarketing(0L);

        Set<ConstraintViolation<DecisionInput>> violaciones = validator.validate(input);

        assertThat(violaciones)
                .as("Un DecisionInput válido no debe tener violaciones")
                .isEmpty();
    }
}
