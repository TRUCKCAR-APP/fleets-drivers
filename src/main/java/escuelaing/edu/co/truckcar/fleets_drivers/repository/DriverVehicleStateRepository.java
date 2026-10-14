package escuelaing.edu.co.truckcar.fleets_drivers.repository;

import escuelaing.edu.co.truckcar.fleets_drivers.model.DriverVehicleState;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DriverVehicleStateRepository extends CrudRepository<DriverVehicleState, String> {
    Optional<DriverVehicleState> findByVehiclePlate(String vehiclePlate);
}