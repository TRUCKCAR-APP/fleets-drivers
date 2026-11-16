package escuelaing.edu.co.truckcar.fleets_drivers.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato de errores RFC 7807 con los campos de antes (message, error, details, timestamp). */
class ProblemDetailContractTest {

    @RestController
    static class ProbeController {
        record Body(@NotBlank(message = "La placa del vehículo es obligatoria") String vehiclePlate) { }

        @GetMapping("/fleet/plate")
        String plate() {
            throw new PlateAlreadyRegisteredException("La placa TDK-410 ya está registrada en el vehículo de otro conductor");
        }

        @GetMapping("/fleet/unavailable")
        String unavailable() {
            throw new LoadAllocationUnavailableException("No se pudo consultar la carga #12 en Load-Allocation");
        }

        @PostMapping("/fleet/validate")
        String validate(@Valid @RequestBody Body body) {
            return "ok";
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void aConflictHasTheStandardFieldsAndKeepsTheOldOnes() throws Exception {
        mvc.perform(get("/fleet/plate").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("https://truckcar.co/errors/plate-already-registered"))
                .andExpect(jsonPath("$.title").value("Placa ya registrada"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.instance").value("/fleet/plate"))
                .andExpect(jsonPath("$.detail").value("La placa TDK-410 ya está registrada en el vehículo de otro conductor"))
                .andExpect(jsonPath("$.message").value("La placa TDK-410 ya está registrada en el vehículo de otro conductor"))
                .andExpect(jsonPath("$.error").value("Placa ya registrada"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void problemJsonOnlyForWhoAsksForIt() throws Exception {
        mvc.perform(get("/fleet/unavailable").accept(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/fleet/unavailable"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void aValidationErrorNowAlsoHasAMessage() throws Exception {
        mvc.perform(post("/fleet/validate").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehiclePlate\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.vehiclePlate").value("La placa del vehículo es obligatoria"))
                .andExpect(jsonPath("$.errors[0].field").value("vehiclePlate"))
                .andExpect(jsonPath("$.message").value("La placa del vehículo es obligatoria"));
    }
}
