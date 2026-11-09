package escuelaing.edu.co.truckcar.fleets_drivers.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Los replanteos de route-ai-agent con posición llegan en vivo a las PWA por /topic/road-blockages. */
class RoadBlockageListenerTest {

    private static final Instant NOW = Instant.parse("2026-11-08T15:00:00Z");

    private SimpMessagingTemplate messaging;
    private RoadBlockageListener listener;

    @BeforeEach
    void setUp() {
        messaging = mock(SimpMessagingTemplate.class);
        listener = new RoadBlockageListener(messaging, new ObjectMapper());
        listener.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String replan(String occurredAt, String position) {
        return """
                {"eventId": "e-9", "eventType": "route.replan.requested", "occurredAt": "%s", "producer": "route-ai-agent",
                 "payload": {"alertId": 21, %s "alertType": "DERRUMBE", "title": "Derrumbe",
                   "description": "Derrumbe tapa la vía en el km 40", "severity": "HIGH", "confidence": 0.9,
                   "source": "AZURE_OPENAI", "reason": "AZURE_OPENAI detectó bloqueo", "affectedCorridor": ""}}"""
                .formatted(occurredAt, position);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFreshBlockageWithPositionIsForwardedToTheDrivers() {
        listener.handleRouteEvent(replan("2026-11-08T14:55:00Z", "\"latitude\": 4.7012, \"longitude\": -74.0425,"));

        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(eq("/topic/road-blockages"), sent.capture());
        Map<String, Object> blockage = (Map<String, Object>) sent.getValue();
        assertThat(blockage).containsEntry("alertId", 21L)
                .containsEntry("latitude", 4.7012)
                .containsEntry("longitude", -74.0425)
                .containsEntry("description", "Derrumbe tapa la vía en el km 40")
                .containsEntry("confidence", 0.9)
                .containsEntry("source", "AZURE_OPENAI")
                .containsEntry("affectedCorridor", null)
                .containsEntry("occurredAt", "2026-11-08T14:55:00Z");
    }

    @Test
    void anOldBlockageIsNotAnnouncedAgain() {
        listener.handleRouteEvent(replan("2026-11-08T14:00:00Z", "\"latitude\": 4.7012, \"longitude\": -74.0425,"));

        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void withoutPositionTheDriversCannotLocateItAndNothingIsSent() {
        listener.handleRouteEvent(replan("2026-11-08T14:59:00Z", ""));

        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void otherRouteEventsAndMalformedMessagesAreIgnored() {
        listener.handleRouteEvent("""
                {"eventId": "e-1", "eventType": "route.requested", "payload": {"latitude": 4.7, "longitude": -74.0}}""");
        listener.handleRouteEvent("no es json");

        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }
}
