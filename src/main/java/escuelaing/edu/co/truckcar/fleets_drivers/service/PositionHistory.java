package escuelaing.edu.co.truckcar.fleets_drivers.service;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry.LocationUpdateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Histórico completo de posiciones GPS (tabla position_history). Redis guarda solo la última posición de cada
 * conductor para el mapa en vivo; aquí queda el recorrido, incluido lo registrado sin señal.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PositionHistory {

    public static final String LIVE = "LIVE";
    public static final String OFFLINE = "OFFLINE";

    private final JdbcTemplate jdbc;

    public void record(LocationUpdateMessage update, Instant recordedAt, String source, UUID clientEventId) {
        jdbc.update("""
                INSERT INTO position_history
                    (driver_id, load_id, latitude, longitude, speed_kmh, heading, recorded_at, source, client_event_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                update.getDriverId(), update.getLoadId(), update.getLatitude(), update.getLongitude(),
                update.getSpeedKmH(), update.getHeading(), Timestamp.from(recordedAt), source, clientEventId);
    }

    /** La telemetría en vivo no debe caerse por el histórico: si la base falla, se registra y se sigue. */
    public void recordLive(LocationUpdateMessage update, Instant recordedAt) {
        try {
            record(update, recordedAt, LIVE, null);
        } catch (RuntimeException e) {
            log.warn("No se pudo guardar en el histórico la posición de {}: {}", update.getDriverId(), e.getMessage());
        }
    }
}
