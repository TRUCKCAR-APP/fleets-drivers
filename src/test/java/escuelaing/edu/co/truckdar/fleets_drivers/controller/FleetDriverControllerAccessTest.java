package escuelaing.edu.co.truckdar.fleets_drivers.controller;

import escuelaing.edu.co.truckdar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckdar.fleets_drivers.exception.GlobalExceptionHandler;
import escuelaing.edu.co.truckdar.fleets_drivers.service.IFleetDriverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La flota completa solo la ve la torre de control; los servicios internos (sin usuario) siguen accediendo. */
class FleetDriverControllerAccessTest {

    private IFleetDriverService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(IFleetDriverService.class);
        when(service.getAllActiveVehicles()).thenReturn(List.of(DriverVehicleStateResponse.builder().driverId("ana").build()));
        when(service.getAvailableForNewLoad()).thenReturn(List.of());
        mvc = MockMvcBuilders.standaloneSetup(new FleetDriverController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"COORDINADOR", "ADMIN"})
    void controlTowerListsTheWholeFleet(String role) throws Exception {
        mvc.perform(get("/api/v1/fleets-drivers/active").header("X-Auth-User", "u-1").header("X-Auth-Role", role))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].driverId").value("ana"));
        mvc.perform(get("/api/v1/fleets-drivers/available").header("X-Auth-User", "u-1").header("X-Auth-Role", role))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GENERADOR", "CONDUCTOR", ""})
    void otherRolesGetForbidden(String role) throws Exception {
        mvc.perform(get("/api/v1/fleets-drivers/active").header("X-Auth-User", "u-1").header("X-Auth-Role", role))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/v1/fleets-drivers/available").header("X-Auth-User", "u-1").header("X-Auth-Role", role))
                .andExpect(status().isForbidden());
        verify(service, never()).getAllActiveVehicles();
    }

    @Test
    void internalServiceCallsWithoutUserStillWork() throws Exception {
        mvc.perform(get("/api/v1/fleets-drivers/active")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/fleets-drivers/available")).andExpect(status().isOk());
    }
}
