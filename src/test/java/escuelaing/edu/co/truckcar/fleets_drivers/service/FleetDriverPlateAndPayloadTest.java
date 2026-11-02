package escuelaing.edu.co.truckcar.fleets_drivers.service;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.request.UpdateDriverVehicleStateRequest;
import escuelaing.edu.co.truckcar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.PlateAlreadyRegisteredException;
import escuelaing.edu.co.truckcar.fleets_drivers.model.DriverVehicleState;
import escuelaing.edu.co.truckcar.fleets_drivers.repository.DriverVehicleStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Una placa pertenece a un solo vehículo, y la capacidad de carga queda registrada para el motor de asignación. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FleetDriverPlateAndPayloadTest {

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
        when(repository.findById(anyString())).thenReturn(Optional.empty());
        when(repository.findAllByVehiclePlate(anyString())).thenReturn(List.of());
    }

    private static UpdateDriverVehicleStateRequest request(String driverId, String plate) {
        return UpdateDriverVehicleStateRequest.builder().driverId(driverId).vehiclePlate(plate)
                .maxHeightMeters(4.1).tareWeightTons(16.0).netCargoWeightTons(0.0).maxPayloadTons(34.0).build();
    }

    @Test
    void aPlateOfAnotherDriverIsAConflictAndNothingIsSaved() {
        when(repository.findAllByVehiclePlate("TDK-410"))
                .thenReturn(List.of(DriverVehicleState.builder().driverId("carlos").vehiclePlate("TDK-410").build()));

        assertThatThrownBy(() -> service.saveOrUpdateState(request("ana", " tdk 410 ")))
                .isInstanceOf(PlateAlreadyRegisteredException.class)
                .hasMessageContaining("TDK-410");
        verify(repository, never()).save(any());
        verify(outboxEventService, never()).enqueueAvailabilityEvent(any());
    }

    @Test
    void theOwnerCanUpdateItsVehicleAndThePlateIsStoredInItsCanonicalForm() {
        when(repository.findAllByVehiclePlate("TDK-410"))
                .thenReturn(List.of(DriverVehicleState.builder().driverId("carlos").vehiclePlate("TDK-410").build()));

        DriverVehicleStateResponse saved = service.saveOrUpdateState(request("carlos", "tdk410"));

        assertThat(saved.getVehiclePlate()).isEqualTo("TDK-410");
        assertThat(saved.getMaxPayloadTons()).isEqualTo(34.0);
    }

    @Test
    void platesAreNormalizedToTheColombianForm() {
        assertThat(FleetDriverService.normalizePlate("abc123")).isEqualTo("ABC-123");
        assertThat(FleetDriverService.normalizePlate(" ABC - 123 ")).isEqualTo("ABC-123");
        assertThat(FleetDriverService.normalizePlate("abc 12d")).isEqualTo("ABC-12D");
        assertThat(FleetDriverService.normalizePlate("e2e101")).isEqualTo("E2E101");
    }

    @Test
    void lookingUpAPlateToleratesLegacyDuplicates() {
        when(repository.findAllByVehiclePlate("TDK-410")).thenReturn(List.of(
                DriverVehicleState.builder().driverId("carlos").vehiclePlate("TDK-410").build(),
                DriverVehicleState.builder().driverId("ana").vehiclePlate("TDK-410").build()));

        assertThat(service.getStateByPlate("tdk 410").getDriverId()).isEqualTo("carlos");
    }
}
