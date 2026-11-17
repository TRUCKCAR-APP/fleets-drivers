package escuelaing.edu.co.truckcar.fleets_drivers.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Afiliación para pruebas que no tratan de ella: ningún conductor la tiene informada y todos pueden recibir cargas. */
final class TestAffiliations {

    private TestAffiliations() {
    }

    static DriverAffiliationStore unknownForEveryone() {
        DriverAffiliationStore store = mock(DriverAffiliationStore.class);
        when(store.allowsLoads(any())).thenReturn(true);
        when(store.get(any())).thenReturn(null);
        return store;
    }
}
