package escuelaing.edu.co.truckcar.fleets_drivers.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Solo ACTIVO o INACTIVO: "DISPONIBLE" se guardaba sin error y el motor de asignación nunca proponía cargas. */
class DriverStatusValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<UpdateDriverVehicleStateRequest>> violations(String status) {
        return validator.validateProperty(UpdateDriverVehicleStateRequest.builder().status(status).build(), "status");
    }

    @Test
    void acceptsActiveInactiveAndNoValue() {
        assertThat(violations("ACTIVO")).isEmpty();
        assertThat(violations("inactivo")).isEmpty();
        assertThat(violations(null)).as("sin valor queda ACTIVO").isEmpty();
    }

    @Test
    void rejectsDisplayLabelsWithAMessageThatNamesTheValidValues() {
        for (String label : new String[] {"DISPONIBLE", "EN_TRANSITO", "DESCANSO", "AVAILABLE"}) {
            assertThat(violations(label)).as(label).singleElement()
                    .extracting(ConstraintViolation::getMessage).asString().contains("ACTIVO").contains("INACTIVO");
        }
    }
}
