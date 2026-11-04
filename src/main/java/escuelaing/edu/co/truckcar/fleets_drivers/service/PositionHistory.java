package escuelaing.edu.co.truckcar.fleets_drivers.service;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry.LocationUpdateMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Histórico completo de posiciones GPS (tabla position_history). Redis guarda solo la última posición de cada
 * conductor para el mapa en vivo; aquí queda el recorrido, incluido lo registrado sin señal.
 * <p>
 * La telemetría en vivo no hace un INSERT por mensaje (con 1000 camiones son ~333 por segundo y cada uno ocupaba una
 * conexión de Hikari): se encola en memoria y {@link #flush()} la escribe por lotes cada pocos segundos. La cola es
 * acotada; si la base no da abasto, las posiciones sobrantes se descartan y se cuentan en
 * truckcar_telemetry_history_dropped_total, sin frenar la telemetría. Las posiciones offline siguen escribiéndose en
 * la misma transacción que su registro de ingesta ({@link #record}).
 */
@Slf4j
@Component
public class PositionHistory {

    public static final String LIVE = "LIVE";
    public static final String OFFLINE = "OFFLINE";

    private static final String INSERT = """
            INSERT INTO position_history
                (driver_id, load_id, latitude, longitude, speed_kmh, heading, recorded_at, source, client_event_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;
    private final BlockingQueue<LivePosition> pending;
    private final int batchSize;
    private final Counter written;
    private final Counter dropped;

    @Autowired
    public PositionHistory(JdbcTemplate jdbc, MeterRegistry meters,
                           @Value("${truckcar.telemetry.history.buffer-capacity:50000}") int bufferCapacity,
                           @Value("${truckcar.telemetry.history.batch-size:1000}") int batchSize) {
        this.jdbc = jdbc;
        this.pending = new ArrayBlockingQueue<>(bufferCapacity);
        this.batchSize = batchSize;
        this.written = Counter.builder("truckcar.telemetry.history.written")
                .description("Posiciones en vivo escritas por lotes en position_history").register(meters);
        this.dropped = Counter.builder("truckcar.telemetry.history.dropped")
                .description("Posiciones en vivo descartadas: cola llena o lote fallido").register(meters);
        Gauge.builder("truckcar.telemetry.history.pending", pending, BlockingQueue::size)
                .description("Posiciones en vivo en espera de escribirse").register(meters);
    }

    public void record(LocationUpdateMessage update, Instant recordedAt, String source, UUID clientEventId) {
        jdbc.update(INSERT,
                update.getDriverId(), update.getLoadId(), update.getLatitude(), update.getLongitude(),
                update.getSpeedKmH(), update.getHeading(), Timestamp.from(recordedAt), source, clientEventId);
    }

    /** No toca la base: encola la posición para el próximo lote. La telemetría no espera ni falla por el histórico. */
    public void recordLive(LocationUpdateMessage update, Instant recordedAt) {
        LivePosition position = new LivePosition(update.getDriverId(), update.getLoadId(), update.getLatitude(),
                update.getLongitude(), update.getSpeedKmH(), update.getHeading(), Timestamp.from(recordedAt));
        if (!pending.offer(position)) {
            dropped.increment();
            log.debug("Cola del histórico llena: se descarta la posición de {}", update.getDriverId());
        }
    }

    /** Vacía la cola en lotes; un lote fallido se descarta (y se cuenta) para no repetirlo sin fin. */
    @Scheduled(fixedDelayString = "${truckcar.telemetry.history.flush-interval-ms:3000}")
    public void flush() {
        for (List<LivePosition> batch = new ArrayList<>(batchSize); pending.drainTo(batch, batchSize) > 0;
             batch = new ArrayList<>(batchSize)) {
            try {
                jdbc.batchUpdate(INSERT, batch, batch.size(), (statement, position) -> {
                    statement.setString(1, position.driverId());
                    statement.setObject(2, position.loadId(), Types.BIGINT);
                    statement.setObject(3, position.latitude(), Types.DOUBLE);
                    statement.setObject(4, position.longitude(), Types.DOUBLE);
                    statement.setObject(5, position.speedKmH(), Types.DOUBLE);
                    statement.setObject(6, position.heading(), Types.DOUBLE);
                    statement.setTimestamp(7, position.recordedAt());
                    statement.setString(8, LIVE);
                    statement.setObject(9, null, Types.OTHER);
                });
                written.increment(batch.size());
            } catch (RuntimeException e) {
                dropped.increment(batch.size());
                log.warn("No se pudo guardar en el histórico un lote de {} posiciones: {}", batch.size(), e.getMessage());
            }
        }
    }

    /** Al apagar el pod se escribe lo que quedaba en la cola. */
    @PreDestroy
    void flushOnShutdown() {
        flush();
    }

    int pendingCount() {
        return pending.size();
    }

    private record LivePosition(String driverId, Long loadId, Double latitude, Double longitude, Double speedKmH,
                                Double heading, Timestamp recordedAt) {
    }
}
