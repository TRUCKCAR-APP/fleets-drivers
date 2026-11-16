package escuelaing.edu.co.truckcar.fleets_drivers.service;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckcar.fleets_drivers.service.LoadAllocationClient.LoadSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Quién puede recibir una carga concreta: las reglas del motor de asignación más las de Load-Allocation. */
class LoadEligibilityTest {

    private static final LoadSnapshot MULA_30T = new LoadSnapshot(12L, "TRACTOMULA_3S3", 30.0, Set.of("rechazo"));

    private static DriverVehicleStateResponse.DriverVehicleStateResponseBuilder fit(String id) {
        return DriverVehicleStateResponse.builder().driverId(id).vehicleType("TRACTOMULA_3S3")
                .maxPayloadTons(34.0).netCargoWeightTons(0.0).runtValid(true);
    }

    @Test
    void onlyDriversWhoCanTakeThisLoadRemain() {
        List<DriverVehicleStateResponse> available = List.of(
                fit("apto").build(),
                fit("rechazo").build(),
                fit("ocupado").build(),
                fit("turbo").vehicleType("TURBO").build(),
                fit("pequeno").maxPayloadTons(25.0).build(),
                fit("descanso").restRequiredAlert(true).build(),
                fit("sin-runt").runtValid(false).build(),
                fit("sin-capacidad").maxPayloadTons(null).build());

        List<DriverVehicleStateResponse> eligible = LoadEligibility.eligible(available, MULA_30T, Set.of("ocupado"));

        assertThat(eligible).extracting(DriverVehicleStateResponse::getDriverId).containsExactly("apto");
    }

    @Test
    void eachExclusionSaysWhy() {
        Set<String> busy = Set.of("ocupado");
        assertThat(LoadEligibility.reasonNotEligible(fit("rechazo").build(), MULA_30T, busy))
                .hasValue("ya rechazó o dejó vencer esta carga");
        assertThat(LoadEligibility.reasonNotEligible(fit("ocupado").build(), MULA_30T, busy))
                .hasValue("tiene una propuesta vigente o una carga aceptada");
        assertThat(LoadEligibility.reasonNotEligible(fit("t").vehicleType("TURBO").build(), MULA_30T, busy))
                .hasValue("su vehículo es TURBO y la carga pide TRACTOMULA_3S3");
        assertThat(LoadEligibility.reasonNotEligible(fit("p").maxPayloadTons(34.0).netCargoWeightTons(10.0).build(),
                MULA_30T, busy)).hasValue("capacidad libre 24.0 t < 30.0 t");
        assertThat(LoadEligibility.reasonNotEligible(fit("apto").build(), MULA_30T, busy)).isEmpty();
    }

    @Test
    void aRefrigeratedLoadNeedsColdChainCertification() {
        LoadSnapshot cold = new LoadSnapshot(13L, "REFRIGERADO", 10.0, Set.of());
        DriverVehicleStateResponse.DriverVehicleStateResponseBuilder fridge =
                fit("f").vehicleType("REFRIGERADO").maxPayloadTons(15.0);

        assertThat(LoadEligibility.reasonNotEligible(fridge.build(), cold, Set.of()))
                .hasValue("sin certificación de cadena de frío");
        assertThat(LoadEligibility.reasonNotEligible(fridge.coldChainCertified(true).build(), cold, Set.of())).isEmpty();
    }

    @Test
    void aLoadWithoutTypeOrWeightDoesNotExcludeAnyone() {
        LoadSnapshot loose = new LoadSnapshot(14L, null, null, Set.of());

        assertThat(LoadEligibility.eligible(List.of(fit("a").vehicleType("TURBO").build()), loose, Set.of())).hasSize(1);
    }
}
