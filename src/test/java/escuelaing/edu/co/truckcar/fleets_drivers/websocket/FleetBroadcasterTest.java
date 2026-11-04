package escuelaing.edu.co.truckcar.fleets_drivers.websocket;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry.FleetLocationMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** La torre recibe un solo mensaje por ventana, con la última posición de cada conductor que se movió. */
class FleetBroadcasterTest {

    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final FleetBroadcaster broadcaster = new FleetBroadcaster(messaging);

    private static FleetLocationMessage location(String driverId, double latitude, Instant at) {
        return FleetLocationMessage.builder().driverId(driverId).latitude(latitude).longitude(-74.1).timestamp(at).build();
    }

    @SuppressWarnings("unchecked")
    private List<FleetLocationMessage> sent() {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(eq(TelemetryAccessPolicy.FLEET_TOPIC), payload.capture());
        return (List<FleetLocationMessage>) payload.getValue();
    }

    @Test
    void manyPositionsInAWindowBecomeOneMessageWithTheLatestPerDriver() {
        Instant now = Instant.now();
        for (int i = 0; i < 9; i++) {
            broadcaster.publish(location("ana", 4.0 + i / 10.0, now.plusSeconds(i)));
        }
        broadcaster.publish(location("carlos", 5.0, now));

        broadcaster.flush();

        List<FleetLocationMessage> snapshot = sent();
        assertThat(snapshot).hasSize(2);
        assertThat(snapshot).filteredOn(l -> l.getDriverId().equals("ana"))
                .singleElement().extracting(FleetLocationMessage::getLatitude).isEqualTo(4.8);
    }

    @Test
    void anOlderPositionDoesNotReplaceANewerOne() {
        Instant now = Instant.now();
        broadcaster.publish(location("ana", 4.9, now));
        broadcaster.publish(location("ana", 4.1, now.minusSeconds(30)));

        broadcaster.flush();

        assertThat(sent()).singleElement().extracting(FleetLocationMessage::getLatitude).isEqualTo(4.9);
    }

    @Test
    void anEmptyWindowSendsNothingAndSentPositionsAreNotRepeated() {
        broadcaster.flush();
        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));

        broadcaster.publish(location("ana", 4.6, Instant.now()));
        broadcaster.flush();
        broadcaster.flush();

        verify(messaging, times(1)).convertAndSend(eq(TelemetryAccessPolicy.FLEET_TOPIC), any(Object.class));
    }
}
