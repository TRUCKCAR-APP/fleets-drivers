package escuelaing.edu.co.truckdar.fleets_drivers.service;

import escuelaing.edu.co.truckdar.fleets_drivers.dto.request.UpdateDriverVehicleStateRequest;
import escuelaing.edu.co.truckdar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckdar.fleets_drivers.dto.telemetry.FleetLocationMessage;
import escuelaing.edu.co.truckdar.fleets_drivers.dto.telemetry.LocationUpdateMessage;
import escuelaing.edu.co.truckdar.fleets_drivers.exception.ResourceNotFoundException;
import escuelaing.edu.co.truckdar.fleets_drivers.model.DriverVehicleState;
import escuelaing.edu.co.truckdar.fleets_drivers.repository.DriverVehicleStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class FleetDriverService implements IFleetDriverService {

    private final DriverVehicleStateRepository repository;
    private final OutboxEventService outboxEventService;

    private static final Set<String> COMMITTED_LOAD_STATUSES = Set.of("ASSIGNED", "IN_TRANSIT");

    @org.springframework.beans.factory.annotation.Value("${truckdar.shift.max-daily-driving-hours:9.0}")
    private Double maxDailyDrivingHours;

    @Override
    @Transactional
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
        // El formulario no conoce las cargas ni el rumbo GPS: se conservan los que ya estaban registrados.
        DriverVehicleState existing = repository.findById(request.getDriverId()).orElse(null);
        entity.setActiveLoads(existing == null ? new HashMap<>() : activeLoadsOf(existing));
        entity.setCurrentHeading(existing == null ? null : existing.getCurrentHeading());

        DriverVehicleState saved = repository.save(entity);

        outboxEventService.enqueueAvailabilityEvent(saved);
        log.info("Estado de disponibilidad encolado en Outbox para la placa: {}", saved.getVehiclePlate());

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

    /** Conductores ACTIVO sin carga aceptada ni en ruta: los únicos que pueden recibir una carga nueva. */
    @Override
    public List<DriverVehicleStateResponse> getAvailableForNewLoad() {
        return StreamSupport.stream(repository.findAll().spliterator(), false)
                .filter(state -> state != null && "ACTIVO".equalsIgnoreCase(state.getStatus()))
                .filter(state -> activeLoadsOf(state).isEmpty())
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Refleja el status de una carga en el conductor asignado: ASSIGNED/IN_TRANSIT la marca activa y
     * cualquier otro (DELIVERED, CANCELLED...) la libera. Es idempotente y no depende del orden entre cargas.
     */
    @Override
    public FleetLocationMessage recordLocation(LocationUpdateMessage update) {
        DriverVehicleState state = repository.findById(update.getDriverId())
                .orElseThrow(() -> new ResourceNotFoundException("El conductor " + update.getDriverId()
                        + " no tiene vehículo registrado en la flota"));
        LocalDateTime now = LocalDateTime.now();
        state.setCurrentLatitude(update.getLatitude());
        state.setCurrentLongitude(update.getLongitude());
        if (update.getSpeedKmH() != null) {
            state.setCurrentSpeedKmH(update.getSpeedKmH());
        }
        if (update.getHeading() != null) {
            state.setCurrentHeading(update.getHeading());
        }
        state.setLastUpdate(now);
        // Telemetría frecuente: se guarda la posición sin encolar un evento de disponibilidad por cada punto.
        repository.save(state);

        // Solo se asocia la posición a una carga si es una carga activa del conductor (ASSIGNED/IN_TRANSIT).
        Map<String, String> active = activeLoadsOf(state);
        String loadStatus = update.getLoadId() == null ? null : active.get(update.getLoadId().toString());
        return FleetLocationMessage.builder()
                .driverId(state.getDriverId())
                .driverName(state.getDriverName())
                .vehiclePlate(state.getVehiclePlate())
                .latitude(state.getCurrentLatitude())
                .longitude(state.getCurrentLongitude())
                .speedKmH(state.getCurrentSpeedKmH())
                .heading(state.getCurrentHeading())
                .loadId(loadStatus == null ? null : update.getLoadId())
                .loadStatus(loadStatus)
                .timestamp(now.atZone(java.time.ZoneId.systemDefault()).toInstant())
                .build();
    }

    @Override
    public boolean isActiveLoadOf(String driverId, Long loadId) {
        return driverId != null && loadId != null && repository.findById(driverId)
                .map(state -> activeLoadsOf(state).containsKey(loadId.toString()))
                .orElse(false);
    }

    @Override
    public boolean applyLoadStatus(String driverId, Long loadId, String loadStatus) {
        if (driverId == null || loadId == null || loadStatus == null) {
            return false;
        }
        DriverVehicleState state = repository.findById(driverId).orElse(null);
        if (state == null) {
            log.debug("Carga #{} ({}) ignorada: el conductor {} no tiene estado en la flota", loadId, loadStatus, driverId);
            return false;
        }
        Map<String, String> active = activeLoadsOf(state);
        String key = loadId.toString();
        boolean changed = COMMITTED_LOAD_STATUSES.contains(loadStatus)
                ? !loadStatus.equals(active.put(key, loadStatus))
                : active.remove(key) != null;
        if (!changed) {
            return false;
        }
        state.setActiveLoads(active);
        repository.save(state);
        log.info("Conductor {}: carga #{} -> {}; cargas activas {}", driverId, loadId, loadStatus, active);
        return true;
    }

    private Map<String, String> activeLoadsOf(DriverVehicleState state) {
        return state.getActiveLoads() == null ? new HashMap<>() : new HashMap<>(state.getActiveLoads());
    }

    private DriverVehicleStateResponse mapToResponse(DriverVehicleState entity) {
        Map.Entry<String, String> current = activeLoadsOf(entity).entrySet().stream()
                .min(Comparator.comparing((Map.Entry<String, String> e) -> !"IN_TRANSIT".equals(e.getValue()))
                        .thenComparing(e -> -Long.parseLong(e.getKey())))
                .orElse(null);
        return DriverVehicleStateResponse.builder()
                .currentLoadId(current == null ? null : Long.valueOf(current.getKey()))
                .currentLoadStatus(current == null ? null : current.getValue())
                .onTrip(current != null)
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
                .currentHeading(entity.getCurrentHeading())
                .status(entity.getStatus())
                .lastUpdate(entity.getLastUpdate())
                .build();
    }
}