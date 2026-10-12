package escuelaing.edu.co.truckdar.fleets_drivers.service;

import escuelaing.edu.co.truckdar.fleets_drivers.dto.request.UpdateDriverVehicleStateRequest;
import escuelaing.edu.co.truckdar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckdar.fleets_drivers.dto.telemetry.FleetLocationMessage;
import escuelaing.edu.co.truckdar.fleets_drivers.dto.telemetry.LocationUpdateMessage;

import java.util.List;

public interface IFleetDriverService {
    DriverVehicleStateResponse saveOrUpdateState(UpdateDriverVehicleStateRequest request);
    DriverVehicleStateResponse getStateByDriverId(String driverId);
    DriverVehicleStateResponse getStateByPlate(String plate);
    List<DriverVehicleStateResponse> getAllActiveVehicles();
    List<DriverVehicleStateResponse> getAvailableForNewLoad();
    boolean applyLoadStatus(String driverId, Long loadId, String loadStatus);
    /** Telemetría GPS: actualiza la posición del conductor y devuelve el mensaje a retransmitir. */
    FleetLocationMessage recordLocation(LocationUpdateMessage update);
    /** true si loadId es una carga ASSIGNED/IN_TRANSIT del conductor (según los eventos de Load-Allocation). */
    boolean isActiveLoadOf(String driverId, Long loadId);
}