package escuelaing.edu.co.truckdar.fleets_drivers.service;

import escuelaing.edu.co.truckdar.fleets_drivers.dto.request.UpdateDriverVehicleStateRequest;
import escuelaing.edu.co.truckdar.fleets_drivers.dto.response.DriverVehicleStateResponse;

import java.util.List;

public interface IFleetDriverService {
    DriverVehicleStateResponse saveOrUpdateState(UpdateDriverVehicleStateRequest request);
    DriverVehicleStateResponse getStateByDriverId(String driverId);
    DriverVehicleStateResponse getStateByPlate(String plate);
    List<DriverVehicleStateResponse> getAllActiveVehicles();
    List<DriverVehicleStateResponse> getAvailableForNewLoad();
    boolean applyLoadStatus(String driverId, Long loadId, String loadStatus);
}