package escuelaing.edu.co.truckcar.fleets_drivers.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import escuelaing.edu.co.truckcar.fleets_drivers.service.IFleetDriverService;
import escuelaing.edu.co.truckcar.fleets_drivers.websocket.LoadStatusBroadcaster;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** El peso de la carga viaja en el evento de Load-Allocation y pasa a la carga a bordo del conductor. */
class LoadLifecycleEventListenerTest {

    private final IFleetDriverService service = mock(IFleetDriverService.class);
    private final LoadStatusBroadcaster broadcaster = mock(LoadStatusBroadcaster.class);
    private final LoadLifecycleEventListener listener =
            new LoadLifecycleEventListener(service, new ObjectMapper(), broadcaster);

    @Test
    void passesTheLoadWeight() {
        listener.handleLoadEvent("""
                {"eventType":"trip.started","payload":{"loadId":2,"loadStatus":"IN_TRANSIT",
                 "assignedDriverId":"d-1","weightTons":2.5}}""");
        verify(service).applyLoadStatus("d-1", 2L, "IN_TRANSIT", 2.5);
    }

    @Test
    void withoutWeightItStillTracksTheStatus() {
        listener.handleLoadEvent("""
                {"payload":{"loadId":2,"loadStatus":"ASSIGNED","assignedDriverId":"d-1"}}""");
        verify(service).applyLoadStatus("d-1", 2L, "ASSIGNED", null);
    }

    @Test
    void ignoresEventsWithoutDriver() {
        listener.handleLoadEvent("{\"payload\":{\"loadId\":2,\"loadStatus\":\"AVAILABLE\",\"weightTons\":2.5}}");
        verify(service, never()).applyLoadStatus(anyString(), anyLong(), anyString(), any());
    }

    /** KAN-47: también un evento sin conductor (sin candidatos) llega a la línea de tiempo del generador. */
    @Test
    void everyEventIsBroadcastEvenWithoutDriver() {
        listener.handleLoadEvent(
                "{\"eventType\":\"assignment.unassigned\",\"payload\":{\"loadId\":2,\"loadStatus\":\"AVAILABLE\"}}");
        verify(broadcaster).broadcast(any());
        verify(service, never()).applyLoadStatus(anyString(), anyLong(), anyString(), any());
    }

    @Test
    void invalidJsonIsDiscardedWithoutBroadcasting() {
        listener.handleLoadEvent("no es json");
        verify(broadcaster, never()).broadcast(any());
    }
}
