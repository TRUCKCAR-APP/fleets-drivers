package escuelaing.edu.co.truckcar.fleets_drivers.controller;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.GlobalExceptionHandler;
import escuelaing.edu.co.truckcar.fleets_drivers.service.IFleetDriverService;
import escuelaing.edu.co.truckcar.fleets_drivers.websocket.LoadOwnershipClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La flota completa solo la ve la torre de control; los servicios internos (sin usuario) siguen accediendo. */
class FleetDriverControllerAccessTest {

    private IFleetDriverService service;
    private LoadOwnershipClient loadOwnershipClient;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(IFleetDriverService.class);
        loadOwnershipClient = mock(LoadOwnershipClient.class);
        when(service.getAllActiveVehicles()).thenReturn(List.of(DriverVehicleStateResponse.builder().driverId("ana").build()));
        when(service.getAvailableForNewLoad()).thenReturn(List.of());
        mvc = MockMvcBuilders.standaloneSetup(new FleetDriverController(service, loadOwnershipClient))
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

    // ------------------------------------------------------------------ POST /state

    private static String state(String driverId) {
        return "{\"driverId\":\"" + driverId + "\",\"vehiclePlate\":\"TDK-410\",\"maxHeightMeters\":4.2,"
                + "\"tareWeightTons\":16,\"netCargoWeightTons\":35,\"currentLatitude\":4.61,\"currentLongitude\":-74.08,"
                + "\"status\":\"ACTIVO\"}";
    }

    private org.springframework.test.web.servlet.ResultActions postState(String body, String user, String role) throws Exception {
        var request = post("/api/v1/fleets-drivers/state").contentType(MediaType.APPLICATION_JSON).content(body);
        if (user != null) request.header("X-Auth-User", user);
        if (role != null) request.header("X-Auth-Role", role);
        return mvc.perform(request);
    }

    @Test
    void driverUpdatesOnlyItsOwnState() throws Exception {
        when(service.saveOrUpdateState(any())).thenReturn(DriverVehicleStateResponse.builder().driverId("ana").build());

        postState(state("ana"), "ana", "CONDUCTOR").andExpect(status().isOk());
        postState(state("otro-conductor"), "ana", "CONDUCTOR")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("su propio estado")));
        verify(service, times(1)).saveOrUpdateState(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"COORDINADOR", "ADMIN"})
    void controlTowerUpdatesAnyDriver(String role) throws Exception {
        postState(state("drv-e2e-1"), "coord-1", role).andExpect(status().isOk());
    }

    @Test
    void internalCallsUpdateAnyDriverButOtherRolesCannot() throws Exception {
        postState(state("drv-e2e-1"), null, null).andExpect(status().isOk());
        postState(state("drv-e2e-1"), "gen-1", "GENERADOR").andExpect(status().isForbidden());
        postState(state("drv-e2e-1"), "x-1", "").andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ GET /drivers/{id} y /vehicles/{plate}

    private org.springframework.test.web.servlet.ResultActions getAs(String path, String user, String role) throws Exception {
        var request = get(path);
        if (user != null) request.header("X-Auth-User", user);
        if (role != null) request.header("X-Auth-Role", role);
        return mvc.perform(request);
    }

    @Test
    void locationLookupsRequireGatewayIdentity() throws Exception {
        getAs("/api/v1/fleets-drivers/drivers/ana", null, null).andExpect(status().isUnauthorized());
        getAs("/api/v1/fleets-drivers/vehicles/TDK-410", null, null).andExpect(status().isUnauthorized());
        getAs("/api/v1/fleets-drivers/drivers/ana", "ana", null).andExpect(status().isUnauthorized());
        verify(service, never()).getStateByDriverId(any());
        verify(service, never()).getStateByPlate(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"COORDINADOR", "ADMIN"})
    void controlTowerSeesAnyDriver(String role) throws Exception {
        when(service.getStateByDriverId("ana")).thenReturn(DriverVehicleStateResponse.builder().driverId("ana").build());
        when(service.getStateByPlate("TDK-410")).thenReturn(DriverVehicleStateResponse.builder().driverId("ana").build());

        getAs("/api/v1/fleets-drivers/drivers/ana", "coord-1", role).andExpect(status().isOk());
        getAs("/api/v1/fleets-drivers/vehicles/TDK-410", "coord-1", role).andExpect(status().isOk());
    }

    @Test
    void driverSeesOnlyItself() throws Exception {
        when(service.getStateByDriverId("ana")).thenReturn(DriverVehicleStateResponse.builder().driverId("ana").build());
        when(service.getStateByPlate("TDK-410")).thenReturn(DriverVehicleStateResponse.builder().driverId("ana").build());

        getAs("/api/v1/fleets-drivers/drivers/ana", "ana", "CONDUCTOR").andExpect(status().isOk());
        getAs("/api/v1/fleets-drivers/vehicles/TDK-410", "ana", "CONDUCTOR").andExpect(status().isOk());
        getAs("/api/v1/fleets-drivers/drivers/ana", "luis", "CONDUCTOR").andExpect(status().isForbidden());
        getAs("/api/v1/fleets-drivers/vehicles/TDK-410", "luis", "CONDUCTOR").andExpect(status().isForbidden());
        verify(service, never()).getStateByDriverId("luis");
    }

    @Test
    void generatorSeesOnlyDriversCarryingItsLoads() throws Exception {
        when(service.getStateByDriverId("ana")).thenReturn(DriverVehicleStateResponse.builder().driverId("ana").build());
        when(service.getActiveLoadIds("ana")).thenReturn(List.of(30L));
        when(loadOwnershipClient.isOwnedByGenerator(30L, "gen-1")).thenReturn(true);

        getAs("/api/v1/fleets-drivers/drivers/ana", "gen-1", "GENERADOR").andExpect(status().isOk());
        getAs("/api/v1/fleets-drivers/drivers/ana", "gen-2", "GENERADOR").andExpect(status().isForbidden());
    }

    @Test
    void unknownPlateDoesNotRevealExistenceToDrivers() throws Exception {
        when(service.getStateByPlate("XXX-000"))
                .thenThrow(new escuelaing.edu.co.truckcar.fleets_drivers.exception.ResourceNotFoundException("no existe"));

        getAs("/api/v1/fleets-drivers/vehicles/XXX-000", "ana", "CONDUCTOR").andExpect(status().isForbidden());
    }
}
