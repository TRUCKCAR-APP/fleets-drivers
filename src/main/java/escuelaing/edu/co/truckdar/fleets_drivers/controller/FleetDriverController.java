package escuelaing.edu.co.truckdar.fleets_drivers.controller;

import escuelaing.edu.co.truckdar.fleets_drivers.dto.request.UpdateDriverVehicleStateRequest;
import escuelaing.edu.co.truckdar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckdar.fleets_drivers.service.IFleetDriverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fleets-drivers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FleetDriverController {

    private final IFleetDriverService service;

    @PostMapping("/state")
    public ResponseEntity<DriverVehicleStateResponse> updateState(
            @Valid @RequestBody UpdateDriverVehicleStateRequest request) {
        return ResponseEntity.ok(service.saveOrUpdateState(request));
    }

    @GetMapping("/drivers/{driverId}")
    public ResponseEntity<DriverVehicleStateResponse> getByDriver(@PathVariable String driverId) {
        return ResponseEntity.ok(service.getStateByDriverId(driverId));
    }

    @GetMapping("/vehicles/{plate}")
    public ResponseEntity<DriverVehicleStateResponse> getByPlate(@PathVariable String plate) {
        return ResponseEntity.ok(service.getStateByPlate(plate));
    }

    @GetMapping("/active")
    public ResponseEntity<List<DriverVehicleStateResponse>> getAllActive() {
        return ResponseEntity.ok(service.getAllActiveVehicles());
    }

    /** Conductores ACTIVO sin carga aceptada ni en ruta: candidatos para el motor de asignación. */
    @GetMapping("/available")
    public ResponseEntity<List<DriverVehicleStateResponse>> getAvailable() {
        return ResponseEntity.ok(service.getAvailableForNewLoad());
    }
}