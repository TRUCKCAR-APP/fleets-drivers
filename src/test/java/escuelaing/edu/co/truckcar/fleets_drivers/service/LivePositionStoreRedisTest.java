package escuelaing.edu.co.truckcar.fleets_drivers.service;

import escuelaing.edu.co.truckcar.fleets_drivers.model.DriverVehicleState;
import escuelaing.edu.co.truckcar.fleets_drivers.repository.DriverVehicleStateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contra el Redis local: lo que escribe el HSET de posición lo lee el repositorio de Spring Data igual que antes, y los
 * demás campos (cargas activas, placa, nombre) no se tocan.
 */
@DataRedisTest
@Import(LivePositionStore.class)
class LivePositionStoreRedisTest {

    @Autowired
    private LivePositionStore store;

    @Autowired
    private DriverVehicleStateRepository repository;

    @Autowired
    private StringRedisTemplate redis;

    private final String driverId = "test-live-" + UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        repository.deleteById(driverId);
    }

    @Test
    void positionFieldsAreUpdatedAndTheRestOfTheHashIsKept() {
        repository.save(DriverVehicleState.builder().driverId(driverId).driverName("Ana Torres").vehiclePlate("TST-001")
                .currentLatitude(1.0).currentLongitude(1.0).status("ACTIVO")
                .activeLoads(new HashMap<>(Map.of("30", "IN_TRANSIT"))).build());
        LocalDateTime at = LocalDateTime.of(2026, 11, 3, 14, 5, 9, 123_000_000);

        assertThat(store.updatePosition(driverId, 4.61, -74.08, 62.5, 210.0, at)).isTrue();

        DriverVehicleState read = repository.findById(driverId).orElseThrow();
        assertThat(read.getCurrentLatitude()).isEqualTo(4.61);
        assertThat(read.getCurrentLongitude()).isEqualTo(-74.08);
        assertThat(read.getCurrentSpeedKmH()).isEqualTo(62.5);
        assertThat(read.getCurrentHeading()).isEqualTo(210.0);
        assertThat(read.getLastUpdate()).isEqualTo(at);
        assertThat(read.getDriverName()).isEqualTo("Ana Torres");
        assertThat(read.getActiveLoads()).containsEntry("30", "IN_TRANSIT");
        assertThat(repository.findByVehiclePlate("TST-001")).isPresent();
    }

    @Test
    void aMissingDriverIsNotCreatedByAPosition() {
        assertThat(store.updatePosition(driverId, 4.61, -74.08, null, null, LocalDateTime.now())).isFalse();

        assertThat(redis.hasKey(LivePositionStore.KEY_PREFIX + driverId)).isFalse();
    }
}
