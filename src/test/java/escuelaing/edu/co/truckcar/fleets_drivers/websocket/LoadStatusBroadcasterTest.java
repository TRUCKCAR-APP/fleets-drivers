package escuelaing.edu.co.truckcar.fleets_drivers.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** KAN-47: cada cambio de estado de una carga llega a /topic/trip.{loadId}.status. */
class LoadStatusBroadcasterTest {

    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final LoadStatusBroadcaster broadcaster = new LoadStatusBroadcaster(messaging);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void anAcceptedProposalIsPublishedWithWhatTheTimelineNeeds() throws Exception {
        broadcaster.broadcast(mapper.readTree("""
                {"eventType":"assignment.accepted","occurredAt":"2026-11-13T22:00:00Z",
                 "payload":{"loadId":21,"loadStatus":"ASSIGNED","assignmentStatus":"ACCEPTED",
                            "assignedDriverId":"d-1","assignedDriverName":"Carlos","assignedVehiclePlate":"TDK-410",
                            "assignmentAttempts":2,"offeredBudget":1500000}}"""));

        ArgumentCaptor<Map<String, Object>> message = ArgumentCaptor.forClass(Map.class);
        verify(messaging).convertAndSend(eq("/topic/trip.21.status"), message.capture());
        assertThat(message.getValue())
                .containsEntry("loadId", 21L)
                .containsEntry("eventType", "assignment.accepted")
                .containsEntry("loadStatus", "ASSIGNED")
                .containsEntry("assignmentStatus", "ACCEPTED")
                .containsEntry("assignedDriverName", "Carlos")
                .containsEntry("assignedVehiclePlate", "TDK-410")
                .containsEntry("assignmentAttempts", 2)
                .containsEntry("occurredAt", "2026-11-13T22:00:00Z")
                // Solo lo que la línea de tiempo necesita: ni el id del conductor ni el presupuesto.
                .doesNotContainKeys("assignedDriverId", "offeredBudget");
    }

    @ParameterizedTest
    @ValueSource(strings = {"assignment.proposed", "assignment.notified", "assignment.rejected", "assignment.timeout",
            "assignment.unassigned", "trip.started", "trip.delivered", "load.cancelled"})
    void everyLifecycleEventIsPublished(String eventType) throws Exception {
        broadcaster.broadcast(mapper.readTree("{\"eventType\":\"" + eventType + "\",\"payload\":{\"loadId\":7}}"));
        verify(messaging).convertAndSend(eq("/topic/trip.7.status"), any(Object.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"eventType\":\"load.published\",\"payload\":{\"loadId\":7}}",
            "{\"eventType\":\"otro.evento\",\"payload\":{\"loadId\":7}}",
            "{\"payload\":{\"loadId\":7}}",
            "{\"eventType\":\"trip.started\",\"payload\":{}}",
            "{\"eventType\":\"trip.started\",\"payload\":{\"loadId\":\"abc\"}}"})
    void otherEventsOrIncompleteOnesAreNotPublished(String json) throws Exception {
        broadcaster.broadcast(mapper.readTree(json));
        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void aBrokerFailureDoesNotPropagate() throws Exception {
        doThrow(new MessagingException("relay caído")).when(messaging).convertAndSend(anyString(), any(Object.class));

        assertThatCode(() -> broadcaster.broadcast(mapper.readTree(
                "{\"eventType\":\"trip.started\",\"payload\":{\"loadId\":7}}"))).doesNotThrowAnyException();
    }
}
