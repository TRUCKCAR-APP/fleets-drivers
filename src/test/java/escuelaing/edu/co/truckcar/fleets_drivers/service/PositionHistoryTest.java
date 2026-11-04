package escuelaing.edu.co.truckcar.fleets_drivers.service;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry.LocationUpdateMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.time.Instant;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** La telemetría en vivo se encola y se escribe por lotes: ningún INSERT por mensaje. */
class PositionHistoryTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    private PositionHistory history(int capacity, int batchSize) {
        return new PositionHistory(jdbc, meters, capacity, batchSize);
    }

    private static LocationUpdateMessage position(String driverId) {
        return LocationUpdateMessage.builder().driverId(driverId).latitude(4.6).longitude(-74.1).speedKmH(60.0)
                .heading(90.0).loadId(30L).build();
    }

    private double counter(String name) {
        return meters.get(name).counter().count();
    }

    @Test
    void livePositionsAreQueuedWithoutTouchingTheDatabase() {
        PositionHistory history = history(100, 50);

        for (int i = 0; i < 10; i++) {
            history.recordLive(position("ana"), Instant.now());
        }

        verifyNoInteractions(jdbc);
        assertThat(history.pendingCount()).isEqualTo(10);
        assertThat(meters.get("truckcar.telemetry.history.pending").gauge().value()).isEqualTo(10);
    }

    @Test
    @SuppressWarnings("unchecked")
    void flushWritesTheQueueInBatches() {
        PositionHistory history = history(100, 4);
        for (int i = 0; i < 10; i++) {
            history.recordLive(position("driver-" + i), Instant.now());
        }

        history.flush();

        // 10 posiciones en lotes de 4: 4 + 4 + 2, una sola sentencia por lote.
        verify(jdbc, times(3)).batchUpdate(anyString(), any(Collection.class), anyInt(),
                any(ParameterizedPreparedStatementSetter.class));
        verify(jdbc).batchUpdate(anyString(), argThat((Collection<?> rows) -> rows.size() == 2), eq(2),
                any(ParameterizedPreparedStatementSetter.class));
        assertThat(history.pendingCount()).isZero();
        assertThat(counter("truckcar.telemetry.history.written")).isEqualTo(10);
    }

    @Test
    void anEmptyQueueDoesNotTouchTheDatabase() {
        history(100, 50).flush();

        verifyNoInteractions(jdbc);
    }

    @Test
    void whenTheQueueIsFullPositionsAreDroppedAndCounted() {
        PositionHistory history = history(3, 50);

        for (int i = 0; i < 5; i++) {
            history.recordLive(position("ana"), Instant.now());
        }

        assertThat(history.pendingCount()).isEqualTo(3);
        assertThat(counter("truckcar.telemetry.history.dropped")).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aDatabaseFailureDropsTheBatchWithoutBreakingTelemetry() {
        when(jdbc.batchUpdate(anyString(), any(Collection.class), anyInt(), any(ParameterizedPreparedStatementSetter.class)))
                .thenThrow(new DataAccessResourceFailureException("pool agotado"));
        PositionHistory history = history(100, 50);
        history.recordLive(position("ana"), Instant.now());
        history.recordLive(position("carlos"), Instant.now());

        assertThatCode(history::flush).doesNotThrowAnyException();

        assertThat(history.pendingCount()).isZero();
        assertThat(counter("truckcar.telemetry.history.dropped")).isEqualTo(2);
        // La siguiente posición vuelve a encolarse con normalidad.
        history.recordLive(position("ana"), Instant.now());
        assertThat(history.pendingCount()).isEqualTo(1);
    }

    @Test
    void offlinePositionsAreStillWrittenImmediately() {
        history(100, 50).record(position("ana"), Instant.now(), PositionHistory.OFFLINE, null);

        verify(jdbc).update(anyString(), any(Object[].class));
    }
}
