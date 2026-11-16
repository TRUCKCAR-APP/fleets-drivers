package escuelaing.edu.co.truckcar.fleets_drivers.service;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckcar.fleets_drivers.service.LoadAllocationClient.LoadSnapshot;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Quién puede recibir una carga concreta entre los conductores disponibles. Son las mismas reglas con las que el motor
 * de asignación (AI-powered-fleet-allocation, FleetScoringOptimizer) descarta candidatos, más las que solo conoce
 * Load-Allocation: así la torre de control no ve como elegible a alguien que después responde 409 al asignarlo.
 * <p>
 * No incluye el filtro de gálibo y peso por ruta (lo aplica el motor con Routing-Restrictions).
 */
public final class LoadEligibility {

    private LoadEligibility() {
    }

    public static List<DriverVehicleStateResponse> eligible(List<DriverVehicleStateResponse> available, LoadSnapshot load,
                                                            Set<String> committedDriverIds) {
        return available.stream()
                .filter(driver -> reasonNotEligible(driver, load, committedDriverIds).isEmpty())
                .toList();
    }

    /** El motivo por el que el conductor no puede recibir la carga, o vacío si puede. */
    public static Optional<String> reasonNotEligible(DriverVehicleStateResponse driver, LoadSnapshot load,
                                                     Set<String> committedDriverIds) {
        if (load.discardedDriverIds().contains(driver.getDriverId())) {
            return Optional.of("ya rechazó o dejó vencer esta carga");
        }
        if (committedDriverIds.contains(driver.getDriverId())) {
            return Optional.of("tiene una propuesta vigente o una carga aceptada");
        }
        if (driver.isRestRequiredAlert()) {
            return Optional.of("debe tomar el descanso obligatorio");
        }
        if (!driver.isRuntValid()) {
            return Optional.of("RUNT no vigente");
        }
        if (load.requiredTruckType() != null && !load.requiredTruckType().equalsIgnoreCase(driver.getVehicleType())) {
            return Optional.of("su vehículo es " + driver.getVehicleType() + " y la carga pide " + load.requiredTruckType());
        }
        if ("REFRIGERADO".equalsIgnoreCase(load.requiredTruckType()) && !driver.isColdChainCertified()) {
            return Optional.of("sin certificación de cadena de frío");
        }
        Double free = availablePayloadTons(driver);
        if (free == null) {
            return Optional.of("sin capacidad de carga registrada");
        }
        if (load.weightTons() != null && free < load.weightTons()) {
            return Optional.of("capacidad libre " + free + " t < " + load.weightTons() + " t");
        }
        return Optional.empty();
    }

    /** Capacidad del vehículo menos la carga a bordo, como la calcula el motor (FleetDriverStateDto). */
    static Double availablePayloadTons(DriverVehicleStateResponse driver) {
        if (driver.getMaxPayloadTons() == null) {
            return null;
        }
        double onBoard = driver.getNetCargoWeightTons() == null ? 0.0 : driver.getNetCargoWeightTons();
        return Math.max(0.0, driver.getMaxPayloadTons() - onBoard);
    }
}
