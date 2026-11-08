package escuelaing.edu.co.truckcar.fleets_drivers.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import escuelaing.edu.co.truckcar.fleets_drivers.service.AccountStatusStore;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El estado de la cuenta del conductor llega por truckcar.events.user-events (sobre común de user-identity). */
class UserAccountEventListenerTest {

    private final AccountStatusStore store = mock(AccountStatusStore.class);
    private final UserAccountEventListener listener = new UserAccountEventListener(store, new ObjectMapper());

    private static String envelope(String type, String payload) {
        return """
                {"eventId": "e-1", "eventType": "%s", "producer": "user-identity", "payload": %s}""".formatted(type, payload);
    }

    @Test
    void aVerificationStoresTheNewStatus() {
        listener.handleUserEvent(envelope("user.status.changed",
                "{\"userId\": \"d-1\", \"role\": \"CONDUCTOR\", \"previousStatus\": \"PENDING_VERIFICATION\", \"status\": \"ACTIVE\"}"));

        verify(store).put("d-1", "ACTIVE");
    }

    @Test
    void theStartupSnapshotAlsoCounts() {
        listener.handleUserEvent(envelope("user.status.changed",
                "{\"userId\": \"d-2\", \"role\": \"CONDUCTOR\", \"previousStatus\": null, \"status\": \"SUSPENDED\"}"));

        verify(store).put("d-2", "SUSPENDED");
    }

    @Test
    void aNewDriverStartsPendingUnlessAStatusAlreadyArrived() {
        listener.handleUserEvent(envelope("user.created", "{\"userId\": \"d-3\", \"role\": \"CONDUCTOR\"}"));
        verify(store).put("d-3", "PENDING_VERIFICATION");

        when(store.get("d-4")).thenReturn("ACTIVE");
        listener.handleUserEvent(envelope("user.created", "{\"userId\": \"d-4\", \"role\": \"CONDUCTOR\"}"));
        verify(store, never()).put("d-4", "PENDING_VERIFICATION");
    }

    @Test
    void otherRolesAndMalformedEventsAreIgnored() {
        listener.handleUserEvent(envelope("user.created", "{\"userId\": \"g-1\", \"role\": \"GENERADOR\"}"));
        listener.handleUserEvent(envelope("user.role.changed", "{\"userId\": \"d-5\", \"newRole\": \"ADMIN\"}"));
        listener.handleUserEvent("no es json");

        verify(store, never()).put(anyString(), any());
    }
}
