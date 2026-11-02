package escuelaing.edu.co.truckcar.fleets_drivers.repository;

import escuelaing.edu.co.truckcar.fleets_drivers.model.DriverVehicleState;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DriverVehicleStateRepository extends CrudRepository<DriverVehicleState, String> {
    Optional<DriverVehicleState> findByVehiclePlate(String vehiclePlate);

    /** Todos los registros con la placa: datos anteriores a la validación de placa única pueden tener más de uno. */
    List<DriverVehicleState> findAllByVehiclePlate(String vehiclePlate);
}