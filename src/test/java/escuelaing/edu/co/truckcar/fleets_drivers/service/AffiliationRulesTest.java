package escuelaing.edu.co.truckcar.fleets_drivers.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import escuelaing.edu.co.truckcar.fleets_drivers.controller.FleetDriverController;
import escuelaing.edu.co.truckcar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry.FleetLocationMessage;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.GlobalExceptionHandler;
import escuelaing.edu.co.truckcar.fleets_drivers.websocket.FleetBroadcaster;
import escuelaing.edu.co.truckcar.fleets_drivers.websocket.LoadOwnershipClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reglas de afiliación: quién recibe cargas y qué ve la torre de control. */
class AffiliationRulesTest {

    @SuppressWarnings("unchecked")
    private static DriverAffiliationStore storeWith(String driverId, String json) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hash = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hash);
        when(hash.get(eq(DriverAffiliationStore.KEY), anyString())).thenReturn(null);
        if (json != null) when(hash.get(DriverAffiliationStore.KEY, driverId)).thenReturn(json);
        return new DriverAffiliationStore(redis, new ObjectMapper());
    }

    @Test
    void aDriverWithoutAChosenTypeDoesNotReceiveLoadsLikeAnIndependent() {
        assertThat(storeWith("x", null).allowsLoads("sin-tipo")).isFalse();
        DriverAffiliationStore independent = storeWith("pedro",
                "{\"driverType\":\"INDEPENDIENTE\",\"loadEligible\":false}");
        assertThat(independent.allowsLoads("pedro")).isFalse();
        assertThat(independent.isIndependent("pedro")).isTrue();
        DriverAffiliationStore affiliated = storeWith("ana",
                "{\"driverType\":\"EMPRESA\",\"companyId\":\"c1\",\"affiliationStatus\":\"ACTIVA\",\"loadEligible\":true}");
        assertThat(affiliated.allowsLoads("ana")).isTrue();
        assertThat(affiliated.isIndependent("ana")).isFalse();
    }

    @Test
    void theTowerDoesNotSeeIndependentDriversButInternalServicesDo() throws Exception {
        IFleetDriverService service = mock(IFleetDriverService.class);
        DriverVehicleStateResponse ana = new DriverVehicleStateResponse();
        ana.setDriverId("ana");
        ana.setDriverType("EMPRESA");
        DriverVehicleStateResponse pedro = new DriverVehicleStateResponse();
        pedro.setDriverId("pedro");
        pedro.setDriverType("INDEPENDIENTE");
        when(service.getAllActiveVehicles()).thenReturn(List.of(ana, pedro));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new FleetDriverController(service, mock(LoadOwnershipClient.class),
                mock(LoadAllocationClient.class))).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/v1/fleets-drivers/active").header("X-Auth-User", "torre").header("X-Auth-Role", "COORDINADOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].driverId", containsInAnyOrder("ana")));
        mvc.perform(get("/api/v1/fleets-drivers/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].driverId", containsInAnyOrder("ana", "pedro")));
    }

    @Test
    void liveTowerPositionsSkipIndependents() {
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        FleetBroadcaster broadcaster = new FleetBroadcaster(messaging);
        DriverAffiliationStore store = mock(DriverAffiliationStore.class);
        when(store.isIndependent("pedro")).thenReturn(true);
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(broadcaster, "setAffiliations", store);
        FleetLocationMessage pedro = new FleetLocationMessage();
        pedro.setDriverId("pedro");
        pedro.setTimestamp(Instant.now());
        broadcaster.publish(pedro);
        broadcaster.flush();
        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
        FleetLocationMessage ana = new FleetLocationMessage();
        ana.setDriverId("ana");
        ana.setTimestamp(Instant.now());
        broadcaster.publish(ana);
        broadcaster.flush();
        verify(messaging).convertAndSend(anyString(), any(Object.class));
    }
}
