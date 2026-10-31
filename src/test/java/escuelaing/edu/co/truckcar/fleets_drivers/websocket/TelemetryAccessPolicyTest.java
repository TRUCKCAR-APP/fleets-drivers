package escuelaing.edu.co.truckcar.fleets_drivers.websocket;

import escuelaing.edu.co.truckcar.fleets_drivers.service.IFleetDriverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelemetryAccessPolicyTest {

    private static final TelemetryPrincipal ANA = new TelemetryPrincipal("ana", "CONDUCTOR");
    private static final TelemetryPrincipal COORD = new TelemetryPrincipal("coord-1", "COORDINADOR");
    private static final TelemetryPrincipal GEN = new TelemetryPrincipal("gen-1", "GENERADOR");

    private IFleetDriverService fleet;
    private LoadOwnershipClient ownership;
    private TelemetryAccessPolicy policy;

    @BeforeEach
    void setUp() {
        fleet = mock(IFleetDriverService.class);
        ownership = mock(LoadOwnershipClient.class);
        policy = new TelemetryAccessPolicy(fleet, ownership);
    }

    @Test
    void driversPublishOnlyTheirOwnPosition() {
        assertThat(policy.canPublish(ANA, "ana")).isTrue();
        assertThat(policy.canPublish(ANA, "otro")).isFalse();
        assertThat(policy.canPublish(COORD, "otro")).isTrue();
        assertThat(policy.canPublish(GEN, "gen-1")).isFalse();
        assertThat(policy.canPublish(null, "ana")).isFalse();
        assertThat(policy.canPublish(ANA, " ")).isFalse();
    }

    @Test
    void wholeFleetTopicIsForTheControlTowerOnly() {
        assertThat(policy.canSubscribe(COORD, "/topic/fleet-locations")).isTrue();
        assertThat(policy.canSubscribe(new TelemetryPrincipal("a", "ADMIN"), "/topic/fleet-locations")).isTrue();
        assertThat(policy.canSubscribe(ANA, "/topic/fleet-locations")).isFalse();
        assertThat(policy.canSubscribe(GEN, "/topic/fleet-locations")).isFalse();
    }

    @Test
    void tripTopicForTowerActiveDriverOrOwningGenerator() {
        when(fleet.isActiveLoadOf("ana", 30L)).thenReturn(true);
        when(ownership.isOwnedByGenerator(30L, "gen-1")).thenReturn(true);

        assertThat(policy.canSubscribe(COORD, "/topic/trip.30")).isTrue();
        assertThat(policy.canSubscribe(ANA, "/topic/trip.30")).isTrue();
        assertThat(policy.canSubscribe(ANA, "/topic/trip.31")).isFalse();
        assertThat(policy.canSubscribe(GEN, "/topic/trip.30")).isTrue();
        assertThat(policy.canSubscribe(GEN, "/topic/trip.31")).isFalse();
        verify(ownership, never()).isOwnedByGenerator(30L, "coord-1");
    }

    @Test
    void unknownOrMalformedDestinationsAreDenied() {
        assertThat(policy.canSubscribe(COORD, "/topic/trip.abc")).isFalse();
        assertThat(policy.canSubscribe(COORD, "/topic/otra-cosa")).isFalse();
        assertThat(policy.canSubscribe(COORD, "/topic/trip.30.extra")).isFalse();
        // El "." es literal: la forma antigua /topic/trip/{id} y cualquier otro separador quedan fuera.
        assertThat(policy.canSubscribe(COORD, "/topic/trip/30")).isFalse();
        assertThat(policy.canSubscribe(COORD, "/topic/tripX30")).isFalse();
        assertThat(policy.canSubscribe(ANA, "/user/queue/errors")).isTrue();
        assertThat(policy.canSubscribe(null, "/user/queue/errors")).isFalse();
    }
}
