package escuelaing.edu.co.truckdar.fleets_drivers.service;

import escuelaing.edu.co.truckdar.fleets_drivers.dto.request.UpdateDriverVehicleStateRequest;
import escuelaing.edu.co.truckdar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckdar.fleets_drivers.model.DriverVehicleState;
import escuelaing.edu.co.truckdar.fleets_drivers.repository.DriverVehicleStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FleetDriverServiceActiveLoadsTest {

    @Mock
    private DriverVehicleStateRepository repository;

    @Mock
    private OutboxEventService outboxEventService;

    private FleetDriverService service;

    @BeforeEach
    void setUp() {
        service = new FleetDriverService(repository, outboxEventService);
        ReflectionTestUtils.setField(service, "maxDailyDrivingHours", 9.0);
        when(repository.save(any(DriverVehicleState.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private DriverVehicleState driver(String id, String status, Map<String, String> activeLoads) {
        DriverVehicleState state = DriverVehicleState.builder()
                .driverId(id)
                .vehiclePlate("P-" + id)
                .status(status)
                .activeLoads(new HashMap<>(activeLoads))
                .build();
        when(repository.findById(id)).thenReturn(Optional.of(state));
        return state;
    }

    @Test
    void acceptedThenStartedThenDeliveredFreesTheDriver() {
        DriverVehicleState ana = driver("ana", "ACTIVO", Map.of());

        assertThat(service.applyLoadStatus("ana", 10L, "ASSIGNED")).isTrue();
        assertThat(ana.getActiveLoads()).containsEntry("10", "ASSIGNED");
        assertThat(service.applyLoadStatus("ana", 10L, "IN_TRANSIT")).isTrue();
        assertThat(ana.getActiveLoads()).containsEntry("10", "IN_TRANSIT");
        assertThat(service.applyLoadStatus("ana", 10L, "DELIVERED")).isTrue();
        assertThat(ana.getActiveLoads()).isEmpty();
    }

    @Test
    void redeliveredEventsAndEventsForUnknownDriversDoNotWrite() {
        driver("ana", "ACTIVO", Map.of("10", "IN_TRANSIT"));
        when(repository.findById("desconocido")).thenReturn(Optional.empty());

        assertThat(service.applyLoadStatus("ana", 10L, "IN_TRANSIT")).isFalse();
        assertThat(service.applyLoadStatus("ana", 99L, "CANCELLED")).isFalse();
        assertThat(service.applyLoadStatus("desconocido", 10L, "ASSIGNED")).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void lateEventOfAnOldLoadDoesNotFreeTheDriverFromTheNewOne() {
        // trip.delivered de la carga 10 llega después de assignment.accepted de la 11 (otra partición).
        DriverVehicleState ana = driver("ana", "ACTIVO", Map.of("10", "IN_TRANSIT"));
        service.applyLoadStatus("ana", 11L, "ASSIGNED");
        service.applyLoadStatus("ana", 10L, "DELIVERED");

        assertThat(ana.getActiveLoads()).containsOnlyKeys("11");
        assertThat(service.getStateByDriverId("ana").isOnTrip()).isTrue();
        assertThat(service.getStateByDriverId("ana").getCurrentLoadId()).isEqualTo(11L);
    }

    @Test
    void availableListsOnlyActiveDriversWithoutLoads() {
        DriverVehicleState free = driver("libre", "ACTIVO", Map.of());
        DriverVehicleState assigned = driver("asignado", "ACTIVO", Map.of("5", "ASSIGNED"));
        DriverVehicleState inTransit = driver("en-ruta", "ACTIVO", Map.of("6", "IN_TRANSIT"));
        DriverVehicleState inactive = driver("inactivo", "INACTIVO", Map.of());
        when(repository.findAll()).thenReturn(List.of(free, assigned, inTransit, inactive));

        assertThat(service.getAvailableForNewLoad()).extracting(DriverVehicleStateResponse::getDriverId)
                .containsExactly("libre");
        assertThat(service.getAllActiveVehicles()).hasSize(4);
    }

    @Test
    void currentLoadPrefersTheTripInTransit() {
        driver("ana", "ACTIVO", Map.of("20", "ASSIGNED", "12", "IN_TRANSIT"));

        DriverVehicleStateResponse response = service.getStateByDriverId("ana");

        assertThat(response.getCurrentLoadId()).isEqualTo(12L);
        assertThat(response.getCurrentLoadStatus()).isEqualTo("IN_TRANSIT");
    }

    @Test
    void telemetryUpdateKeepsTheActiveLoads() {
        driver("ana", "ACTIVO", Map.of("10", "IN_TRANSIT"));
        UpdateDriverVehicleStateRequest telemetry = UpdateDriverVehicleStateRequest.builder()
                .driverId("ana")
                .vehiclePlate("P-ana")
                .drivingHoursToday(3.0)
                .currentLatitude(4.6)
                .currentLongitude(-74.1)
                .status("ACTIVO")
                .build();

        DriverVehicleStateResponse response = service.saveOrUpdateState(telemetry);

        assertThat(response.isOnTrip()).isTrue();
        assertThat(response.getCurrentLoadId()).isEqualTo(10L);
    }

    @Test
    void recordLocationUpdatesPositionAndKeepsOnlyAnActiveLoadId() {
        DriverVehicleState ana = driver("ana", "ACTIVO", Map.of("30", "IN_TRANSIT"));
        ana.setDriverName("Ana Torres");

        var onTrip = service.recordLocation(escuelaing.edu.co.truckdar.fleets_drivers.dto.telemetry.LocationUpdateMessage
                .builder().driverId("ana").latitude(4.7).longitude(-74.2).speedKmH(55.0).heading(180.0).loadId(30L).build());
        var otherLoad = service.recordLocation(escuelaing.edu.co.truckdar.fleets_drivers.dto.telemetry.LocationUpdateMessage
                .builder().driverId("ana").latitude(4.8).longitude(-74.3).loadId(99L).build());

        assertThat(ana.getCurrentLatitude()).isEqualTo(4.8);
        assertThat(ana.getCurrentHeading()).isEqualTo(180.0);
        assertThat(ana.getCurrentSpeedKmH()).isEqualTo(55.0);
        assertThat(onTrip.getLoadId()).isEqualTo(30L);
        assertThat(onTrip.getLoadStatus()).isEqualTo("IN_TRANSIT");
        assertThat(otherLoad.getLoadId()).isNull();
        verify(outboxEventService, never()).enqueueAvailabilityEvent(any());
    }

    @Test
    void recordLocationForADriverWithoutVehicleFails() {
        when(repository.findById("fantasma")).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.recordLocation(
                escuelaing.edu.co.truckdar.fleets_drivers.dto.telemetry.LocationUpdateMessage.builder()
                        .driverId("fantasma").latitude(4.7).longitude(-74.2).build()))
                .isInstanceOf(escuelaing.edu.co.truckdar.fleets_drivers.exception.ResourceNotFoundException.class);
    }
}
