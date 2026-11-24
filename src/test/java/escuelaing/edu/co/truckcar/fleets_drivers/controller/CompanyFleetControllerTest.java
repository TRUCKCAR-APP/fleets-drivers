package escuelaing.edu.co.truckcar.fleets_drivers.controller;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.FleetAccessDeniedException;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.GlobalExceptionHandler;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.IdentityUnavailableException;
import escuelaing.edu.co.truckcar.fleets_drivers.service.DriverAffiliationStore;
import escuelaing.edu.co.truckcar.fleets_drivers.service.DriverAffiliationStore.Affiliation;
import escuelaing.edu.co.truckcar.fleets_drivers.service.IFleetDriverService;
import escuelaing.edu.co.truckcar.fleets_drivers.service.UserIdentityClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * «Mi flota» de una transportadora: solo sus conductores con la afiliación ACTIVA. Andinos tiene a ana (ACTIVA) y a
 * luis (PENDIENTE); Pacífico tiene a jorge; pedro es independiente.
 */
class CompanyFleetControllerTest {

    static final String ANDINOS = "11111111-1111-1111-1111-111111111111";
    static final String PACIFICO = "22222222-2222-2222-2222-222222222222";
    static final String TOKEN_ANDINOS = "Bearer token-andinos";
    static final String TOKEN_PACIFICO = "Bearer token-pacifico";

    private final IFleetDriverService service = mock(IFleetDriverService.class);
    private final DriverAffiliationStore affiliations = mock(DriverAffiliationStore.class);
    private final UserIdentityClient userIdentity = mock(UserIdentityClient.class);
    private MockMvc mvc;

    private static DriverVehicleStateResponse driver(String id) {
        DriverVehicleStateResponse d = new DriverVehicleStateResponse();
        d.setDriverId(id);
        d.setDriverName(id);
        d.setCurrentLatitude(4.6);
        d.setCurrentLongitude(-74.1);
        return d;
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CompanyFleetController(service, affiliations, userIdentity))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(service.getAllActiveVehicles()).thenReturn(List.of(driver("ana"), driver("luis"), driver("jorge"), driver("pedro")));
        when(affiliations.get("ana")).thenReturn(new Affiliation("EMPRESA", ANDINOS, "Transportes Andinos", "ACTIVA", true));
        when(affiliations.get("luis")).thenReturn(new Affiliation("EMPRESA", ANDINOS, "Transportes Andinos", "PENDIENTE", false));
        when(affiliations.get("jorge")).thenReturn(new Affiliation("EMPRESA", PACIFICO, "Carga Pacífico", "ACTIVA", true));
        when(affiliations.get("pedro")).thenReturn(new Affiliation("INDEPENDIENTE", null, null, null, false));
        when(userIdentity.companyIdOf(TOKEN_ANDINOS)).thenReturn(ANDINOS);
        when(userIdentity.companyIdOf(TOKEN_PACIFICO)).thenReturn(PACIFICO);
    }

    @Test
    void eachCompanySeesOnlyItsOwnActiveDrivers() throws Exception {
        mvc.perform(get("/api/v1/fleets-drivers/company/fleet")
                        .header("X-Auth-User", "dueno-andinos").header("X-Auth-Role", "TRANSPORTADORA")
                        .header("Authorization", TOKEN_ANDINOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[*].driverId", containsInAnyOrder("ana")));
        mvc.perform(get("/api/v1/fleets-drivers/company/fleet")
                        .header("X-Auth-User", "dueno-pacifico").header("X-Auth-Role", "TRANSPORTADORA")
                        .header("Authorization", TOKEN_PACIFICO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].driverId", containsInAnyOrder("jorge")));
    }

    @Test
    void otherRolesCannotUseIt() throws Exception {
        for (String role : List.of("CONDUCTOR", "GENERADOR", "COORDINADOR")) {
            mvc.perform(get("/api/v1/fleets-drivers/company/fleet")
                            .header("X-Auth-User", "alguien").header("X-Auth-Role", role)
                            .header("Authorization", TOKEN_ANDINOS))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/fleets-drivers/company/fleet").header("Authorization", TOKEN_ANDINOS))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void aTransportadoraWithoutCompanyIsForbiddenAndIdentityDownIs503() throws Exception {
        when(userIdentity.companyIdOf("Bearer sin-empresa"))
                .thenThrow(new FleetAccessDeniedException("Esta cuenta no tiene una empresa transportadora"));
        mvc.perform(get("/api/v1/fleets-drivers/company/fleet")
                        .header("X-Auth-User", "x").header("X-Auth-Role", "TRANSPORTADORA")
                        .header("Authorization", "Bearer sin-empresa"))
                .andExpect(status().isForbidden());
        when(userIdentity.companyIdOf("Bearer caido"))
                .thenThrow(new IdentityUnavailableException("No se pudo confirmar su empresa"));
        mvc.perform(get("/api/v1/fleets-drivers/company/fleet")
                        .header("X-Auth-User", "x").header("X-Auth-Role", "TRANSPORTADORA")
                        .header("Authorization", "Bearer caido"))
                .andExpect(status().isServiceUnavailable());
    }
}
