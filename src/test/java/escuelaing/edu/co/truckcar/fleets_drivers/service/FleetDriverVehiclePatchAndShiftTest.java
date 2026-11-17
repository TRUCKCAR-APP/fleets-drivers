package escuelaing.edu.co.truckcar.fleets_drivers.service;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.request.PatchVehicleRequest;
import escuelaing.edu.co.truckcar.fleets_drivers.dto.request.UpdateDriverVehicleStateRequest;
import escuelaing.edu.co.truckcar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry.LocationUpdateMessage;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.PlateAlreadyRegisteredException;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.ResourceNotFoundException;
import escuelaing.edu.co.truckcar.fleets_drivers.model.DriverVehicleState;
import escuelaing.edu.co.truckcar.fleets_drivers.repository.DriverVehicleStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lote 3: PATCH de la ficha técnica sin tocar lo operativo, y jornada calculada por el servidor. */
class FleetDriverVehiclePatchAndShiftTest {

    private DriverVehicleStateRepository repository;
    private OutboxEventService outbox;
    private LivePositionStore livePositions;
    private FleetDriverService service;
    private DriverVehicleState ana;

    @BeforeEach
    void setUp() {
        repository = mock(DriverVehicleStateRepository.class);
        outbox = mock(OutboxEventService.class);
        livePositions = mock(LivePositionStore.class);
        service = new FleetDriverService(repository, outbox, livePositions, mock(AccountStatusStore.class),
                TestAffiliations.unknownForEveryone());
        ReflectionTestUtils.setField(service, "maxDailyDrivingHours", 9.0);
        Map<String, String> loads = new HashMap<>(Map.of("12", "IN_TRANSIT"));
        ana = DriverVehicleState.builder().driverId("ana").driverName("Ana").vehiclePlate("TDK-410").vehicleType("TURBO")
                .maxHeightMeters(3.9).maxPayloadTons(8.0).tareWeightTons(6.0).netCargoWeightTons(5.0).grossWeightTons(11.0)
                .runtValid(true).status("ACTIVO").currentLatitude(4.6).currentLongitude(-74.1).currentSpeedKmH(60.0)
                .drivingDay(LocalDateTime.now().toLocalDate()).drivingSecondsToday(3 * 3600L)
                .lastUpdate(LocalDateTime.now().minusMinutes(1)).activeLoads(loads).build();
        when(repository.findById("ana")).thenReturn(Optional.of(ana));
        when(repository.findAllByVehiclePlate(anyString())).thenReturn(List.of());
        when(repository.save(any(DriverVehicleState.class))).thenAnswer(i -> i.getArgument(0));
        when(livePositions.updatePosition(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void patchingTheVehicleChangesOnlyTheFieldsSentAndKeepsTheOperationalState() {
        DriverVehicleStateResponse r = service.patchVehicle("ana", PatchVehicleRequest.builder()
                .maxHeightMeters(4.2).tareWeightTons(7.0).coldChainCertified(true).vehicleType(" refrigerado ").build());

        assertThat(r.getMaxHeightMeters()).isEqualTo(4.2);
        assertThat(r.getTareWeightTons()).isEqualTo(7.0);
        assertThat(r.getGrossWeightTons()).as("tara + carga a bordo").isEqualTo(12.0);
        assertThat(r.isColdChainCertified()).isTrue();
        assertThat(r.getVehicleType()).isEqualTo("REFRIGERADO");
        // Lo que no vino no cambia, y lo operativo se conserva.
        assertThat(r.getMaxPayloadTons()).isEqualTo(8.0);
        assertThat(r.getVehiclePlate()).isEqualTo("TDK-410");
        assertThat(r.isRuntValid()).isTrue();
        assertThat(r.getStatus()).isEqualTo("ACTIVO");
        assertThat(r.getCurrentLatitude()).isEqualTo(4.6);
        assertThat(r.getCurrentLoadId()).isEqualTo(12L);
        assertThat(r.getDrivingHoursToday()).isEqualTo(3.0);
        verify(outbox).enqueueAvailabilityEvent(ana);
    }

    @Test
    void aPlateOfAnotherDriverIsAConflict() {
        when(repository.findAllByVehiclePlate("XYZ-123"))
                .thenReturn(List.of(DriverVehicleState.builder().driverId("luis").vehiclePlate("XYZ-123").build()));

        assertThatThrownBy(() -> service.patchVehicle("ana", PatchVehicleRequest.builder().vehiclePlate("xyz123").build()))
                .isInstanceOf(PlateAlreadyRegisteredException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void aDriverWithoutVehicleMustRegisterItFirst() {
        when(repository.findById("nuevo")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.patchVehicle("nuevo", PatchVehicleRequest.builder().maxHeightMeters(4.0).build()))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("regístrelo primero");
    }

    @Test
    void theDrivingHoursSentByTheAppAreIgnored() {
        DriverVehicleStateResponse r = service.saveOrUpdateState(UpdateDriverVehicleStateRequest.builder()
                .driverId("ana").vehiclePlate("TDK-410").maxHeightMeters(3.9).tareWeightTons(6.0).maxPayloadTons(8.0)
                .drivingHoursToday(0.0).build());

        assertThat(r.getDrivingHoursToday()).as("se conserva la cuenta del servidor").isEqualTo(3.0);
        assertThat(r.getRemainingDrivingHours()).isEqualTo(6.0);
    }

    @Test
    void eachPositionAddsTheTimeMovingToTheShiftInTheSameWrite() {
        LocationUpdateMessage point = new LocationUpdateMessage();
        point.setDriverId("ana");
        point.setLatitude(4.61);
        point.setLongitude(-74.09);
        point.setSpeedKmH(55.0);

        service.recordLocation(point);

        ArgumentCaptor<DrivingTime> driving = ArgumentCaptor.forClass(DrivingTime.class);
        verify(livePositions).updatePosition(eq("ana"), eq(4.61), eq(-74.09), eq(55.0), any(), any(), driving.capture());
        assertThat(driving.getValue().seconds()).as("venía a 60 km/h: se suma el último minuto")
                .isBetween(3 * 3600L + 55, 3 * 3600L + 65);
    }

    @Test
    void yesterdaysShiftReadsAsZeroToday() {
        ana.setDrivingDay(LocalDateTime.now().toLocalDate().minusDays(1));
        ana.setDrivingSecondsToday(8 * 3600L);
        ana.setRestRequiredAlert(true);

        DriverVehicleStateResponse r = service.getStateByDriverId("ana");

        assertThat(r.getDrivingHoursToday()).isZero();
        assertThat(r.getRemainingDrivingHours()).isEqualTo(9.0);
        assertThat(r.isRestRequiredAlert()).isFalse();
    }
}
