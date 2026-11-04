package escuelaing.edu.co.truckcar.fleets_drivers.service;

import escuelaing.edu.co.truckcar.fleets_drivers.model.DriverVehicleState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Escribe en Redis solo los campos de posición del {@link DriverVehicleState} (HSET), en lugar de reescribir el hash
 * completo con el repositorio de Spring Data. Además de mover menos datos, no pisa los campos que cambian otros
 * flujos al mismo tiempo (por ejemplo las cargas activas que llegan por Kafka) ni toca el índice de la placa.
 * <p>
 * Los valores van como texto, igual que los escribe Spring Data Redis (Double y LocalDateTime con toString), así que
 * el repositorio los sigue leyendo sin cambios. El script solo escribe si el hash existe: una posición no puede crear
 * un conductor a medias.
 */
@Component
@RequiredArgsConstructor
public class LivePositionStore {

    /** Mismo keyspace que @RedisHash("driver_vehicle_state"). */
    static final String KEY_PREFIX = "driver_vehicle_state:";

    private static final RedisScript<Long> HSET_IF_EXISTS = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                redis.call('HSET', KEYS[1], unpack(ARGV))
                return 1
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    /** Devuelve false si el conductor ya no existe en Redis. */
    public boolean updatePosition(String driverId, Double latitude, Double longitude, Double speedKmH, Double heading,
                                  LocalDateTime lastUpdate) {
        List<String> fields = new ArrayList<>(10);
        add(fields, "currentLatitude", latitude);
        add(fields, "currentLongitude", longitude);
        add(fields, "currentSpeedKmH", speedKmH);
        add(fields, "currentHeading", heading);
        add(fields, "lastUpdate", lastUpdate);
        Long result = redis.execute(HSET_IF_EXISTS, List.of(KEY_PREFIX + driverId), fields.toArray());
        return result != null && result == 1L;
    }

    private static void add(List<String> fields, String name, Object value) {
        if (value != null) {
            fields.add(name);
            fields.add(value.toString());
        }
    }
}
