package escuelaing.edu.co.truckdar.fleets_drivers.service;

import escuelaing.edu.co.truckdar.fleets_drivers.dto.event.VehicleTelemetryEvent;
import escuelaing.edu.co.truckdar.fleets_drivers.dto.request.UpdateDriverVehicleStateRequest;
import escuelaing.edu.co.truckdar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckdar.fleets_drivers.exception.ResourceNotFoundException;
import escuelaing.edu.co.truckdar.fleets_drivers.model.DriverVehicleState;
import escuelaing.edu.co.truckdar.fleets_drivers.repository.DriverVehicleStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class FleetDriverService implements IFleetDriverService {

    private final DriverVehicleStateRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${truckdar.kafka.topic.vehicle-status:truckdar.events.vehicle-status}")
    private String vehicleStatusTopic;

    @Value("${truckdar.shift.max-daily-driving-hours:9.0}")
    private Double maxDailyDrivingHours;

    @Override
    public DriverVehicleStateResponse saveOrUpdateState(UpdateDriverVehicleStateRequest request) {
        double grossWeight = 0.0;
        if (request.getTareWeightTons() != null) {
            grossWeight += request.getTareWeightTons();
        }
        if (request.getNetCargoWeightTons() != null) {
            grossWeight += request.getNetCargoWeightTons();
        }

        double remainingHours = this.maxDailyDrivingHours;
        boolean restAlert = false;
        if (request.getDrivingHoursToday() != null) {
            remainingHours = Math.max(0.0, this.maxDailyDrivingHours - request.getDrivingHoursToday());
            remainingHours = Math.round(remainingHours * 10.0) / 10.0;
            restAlert = remainingHours <= 2.0;
        }

        LocalDateTime now = LocalDateTime.now();

        DriverVehicleState entity = DriverVehicleState.builder()
                .driverId(request.getDriverId())
                .driverName(request.getDriverName())
                .licenseCategory(request.getLicenseCategory())
                .rating(request.getRating())
                .totalTrips(request.getTotalTrips())
                .vehiclePlate(request.getVehiclePlate())
                .vehicleModel(request.getVehicleModel())
                .vehicleType(request.getVehicleType())
                .maxHeightMeters(request.getMaxHeightMeters())
                .netCargoWeightTons(request.getNetCargoWeightTons())
                .tareWeightTons(request.getTareWeightTons())
                .grossWeightTons(grossWeight)
                .drivingHoursToday(request.getDrivingHoursToday())
                .maxDailyDrivingHours(this.maxDailyDrivingHours)
                .remainingDrivingHours(remainingHours)
                .mandatoryRestLocation(request.getMandatoryRestLocation())
                .restRequiredAlert(restAlert)
                .hazmatAuthorized(request.isHazmatAuthorized())
                .coldChainCertified(request.isColdChainCertified())
                .runtValid(request.isRuntValid())
                .electronicTollTagActive(request.isElectronicTollTagActive())
                .activeBadges(request.getActiveBadges())
                .currentLatitude(request.getCurrentLatitude())
                .currentLongitude(request.getCurrentLongitude())
                .currentSpeedKmH(request.getCurrentSpeedKmH())
                .status(request.getStatus() != null ? request.getStatus() : "ACTIVO")
                .lastUpdate(now)
                .build();

        DriverVehicleState saved = repository.save(entity);

        VehicleTelemetryEvent event = VehicleTelemetryEvent.builder()
                .driverId(saved.getDriverId())
                .vehiclePlate(saved.getVehiclePlate())
                .latitude(saved.getCurrentLatitude())
                .longitude(saved.getCurrentLongitude())
                .speedKmH(saved.getCurrentSpeedKmH())
                .remainingDrivingHours(saved.getRemainingDrivingHours())
                .status(saved.getStatus())
                .timestamp(saved.getLastUpdate())
                .build();

        kafkaTemplate.send(vehicleStatusTopic, saved.getDriverId(), event);
        log.info("Evento emitido a Kafka en tópico [{}] para la placa: {}", vehicleStatusTopic, saved.getVehiclePlate());

        return mapToResponse(saved);
    }

    @Override
    public DriverVehicleStateResponse getStateByDriverId(String driverId) {
        return repository.findById(driverId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró estado para el conductor con ID: " + driverId));
    }

    @Override
    public DriverVehicleStateResponse getStateByPlate(String plate) {
        return repository.findByVehiclePlate(plate)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró vehículo con placa: " + plate));
    }

    @Override
    public List<DriverVehicleStateResponse> getAllActiveVehicles() {
        return StreamSupport.stream(repository.findAll().spliterator(), false)
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private DriverVehicleStateResponse mapToResponse(DriverVehicleState entity) {
        return DriverVehicleStateResponse.builder()
                .driverId(entity.getDriverId())
                .driverName(entity.getDriverName())
                .licenseCategory(entity.getLicenseCategory())
                .rating(entity.getRating())
                .totalTrips(entity.getTotalTrips())
                .vehiclePlate(entity.getVehiclePlate())
                .vehicleModel(entity.getVehicleModel())
                .vehicleType(entity.getVehicleType())
                .maxHeightMeters(entity.getMaxHeightMeters())
                .netCargoWeightTons(entity.getNetCargoWeightTons())
                .tareWeightTons(entity.getTareWeightTons())
                .grossWeightTons(entity.getGrossWeightTons())
                .drivingHoursToday(entity.getDrivingHoursToday())
                .maxDailyDrivingHours(entity.getMaxDailyDrivingHours())
                .remainingDrivingHours(entity.getRemainingDrivingHours())
                .mandatoryRestLocation(entity.getMandatoryRestLocation())
                .restRequiredAlert(entity.isRestRequiredAlert())
                .hazmatAuthorized(entity.isHazmatAuthorized())
                .coldChainCertified(entity.isColdChainCertified())
                .runtValid(entity.isRuntValid())
                .electronicTollTagActive(entity.isElectronicTollTagActive())
                .activeBadges(entity.getActiveBadges())
                .currentLatitude(entity.getCurrentLatitude())
                .currentLongitude(entity.getCurrentLongitude())
                .currentSpeedKmH(entity.getCurrentSpeedKmH())
                .status(entity.getStatus())
                .lastUpdate(entity.getLastUpdate())
                .build();
    }
}