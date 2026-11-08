package escuelaing.edu.co.truckcar.fleets_drivers.service;

import escuelaing.edu.co.truckcar.fleets_drivers.dto.request.UpdateDriverVehicleStateRequest;
import escuelaing.edu.co.truckcar.fleets_drivers.dto.response.DriverVehicleStateResponse;
import escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry.FleetLocationMessage;
import escuelaing.edu.co.truckcar.fleets_drivers.dto.telemetry.LocationUpdateMessage;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.PlateAlreadyRegisteredException;
import escuelaing.edu.co.truckcar.fleets_drivers.exception.ResourceNotFoundException;
import escuelaing.edu.co.truckcar.fleets_drivers.model.DriverVehicleState;
import escuelaing.edu.co.truckcar.fleets_drivers.repository.DriverVehicleStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private final LivePositionStore livePositions;

    private static final Set<String> COMMITTED_LOAD_STATUSES = Set.of("ASSIGNED", "IN_TRANSIT");

    @org.springframework.beans.factory.annotation.Value("${truckcar.shift.max-daily-driving-hours:9.0}")
    private Double maxDailyDrivingHours;

    @Override
    @Transactional
    public DriverVehicleStateResponse saveOrUpdateState(UpdateDriverVehicleStateRequest request) {
        String plate = normalizePlate(request.getVehiclePlate());
        List<String> owners = repository.findAllByVehiclePlate(plate).stream()
                .map(DriverVehicleState::getDriverId)
                .filter(owner -> !owner.equals(request.getDriverId()))
                .toList();
        if (!owners.isEmpty()) {
            throw new PlateAlreadyRegisteredException("La placa " + plate + " ya está registrada en el vehículo de otro conductor");
        }
        // Con cargas en tránsito, la carga a bordo sale de sus pesos (eventos de Load-Allocation): el formulario de la
        // torre no la conoce y no debe dejar el camión "vacío" a mitad de viaje.
        DriverVehicleState existing = repository.findById(request.getDriverId()).orElse(null);
        Map<String, String> activeLoads = existing == null ? new HashMap<>() : activeLoadsOf(existing);
        Map<String, Double> loadWeights = existing == null ? new HashMap<>() : loadWeightsOf(existing);
        // Double.valueOf: con un double en una rama, el ternario desempacaría el null del formulario (NPE).
        Double netCargo = hasLoadOnBoard(activeLoads, loadWeights)
                ? Double.valueOf(onBoardTons(activeLoads, loadWeights))
                : request.getNetCargoWeightTons();
        double grossWeight = grossTons(request.getTareWeightTons(), netCargo);

        double remainingHours = this.maxDailyDrivingHours;
        boolean restAlert = false;
        if (request.getDrivingHoursToday() != null) {
            remainingHours = Math.max(0.0, this.maxDailyDrivingHours - request.getDrivingHoursToday());            remainingHours = Math.round(remainingHours * 10.0) / 10.0;
            restAlert = remainingHours <= 2.0;
        }

        LocalDateTime now = LocalDateTime.now();

        DriverVehicleState entity = DriverVehicleState.builder()
                .driverId(request.getDriverId())
                .driverName(request.getDriverName())
                .licenseCategory(request.getLicenseCategory())
                .rating(request.getRating())
                .totalTrips(request.getTotalTrips())
                .vehiclePlate(plate)
                .vehicleModel(request.getVehicleModel())
                .vehicleType(request.getVehicleType())
                .maxHeightMeters(request.getMaxHeightMeters())
                .netCargoWeightTons(netCargo)
                .maxPayloadTons(request.getMaxPayloadTons())
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
                .status(request.getStatus() != null ? request.getStatus().toUpperCase(java.util.Locale.ROOT) : "ACTIVO")
                .lastUpdate(now)
                .build();
        // El formulario no conoce las cargas ni el rumbo GPS: se conservan los que ya estaban registrados.
        entity.setActiveLoads(activeLoads);
        entity.setActiveLoadWeights(loadWeights);
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
        return repository.findAllByVehiclePlate(normalizePlate(plate)).stream().findFirst()
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
        DriverVehicleState state = requireState(update.getDriverId());
        return applyLocation(state, update, LocalDateTime.now());
    }

    /**
     * Posición registrada sin señal y sincronizada después: solo actualiza el estado vivo si es más reciente que el
     * último dato conocido (una ráfaga tardía no puede hacer "retroceder" al camión en el mapa).
     */
    @Override
    public java.util.Optional<FleetLocationMessage> recordLocationAt(LocationUpdateMessage update,
                                                                     java.time.Instant recordedAt) {
        DriverVehicleState state = requireState(update.getDriverId());
        LocalDateTime at = LocalDateTime.ofInstant(recordedAt, java.time.ZoneId.systemDefault());
        if (state.getLastUpdate() != null && !at.isAfter(state.getLastUpdate())) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(applyLocation(state, update, at));
    }

    private DriverVehicleState requireState(String driverId) {
        return repository.findById(driverId)
                .orElseThrow(() -> new ResourceNotFoundException("El conductor " + driverId
                        + " no tiene vehículo registrado en la flota"));
    }

    private FleetLocationMessage applyLocation(DriverVehicleState state, LocationUpdateMessage update, LocalDateTime now) {
        state.setCurrentLatitude(update.getLatitude());
        state.setCurrentLongitude(update.getLongitude());
        if (update.getSpeedKmH() != null) {
            state.setCurrentSpeedKmH(update.getSpeedKmH());
        }
        if (update.getHeading() != null) {
            state.setCurrentHeading(update.getHeading());
        }
        state.setLastUpdate(now);
        // Telemetría frecuente: HSET de los campos de posición, sin reescribir el hash ni encolar un evento de
        // disponibilidad por cada punto.
        if (!livePositions.updatePosition(state.getDriverId(), state.getCurrentLatitude(), state.getCurrentLongitude(),
                state.getCurrentSpeedKmH(), state.getCurrentHeading(), now)) {
            throw new ResourceNotFoundException("El conductor " + state.getDriverId()
                    + " no tiene vehículo registrado en la flota");
        }

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
    public List<Long> getActiveLoadIds(String driverId) {
        if (driverId == null) {
            return List.of();
        }
        return repository.findById(driverId)
                .map(state -> activeLoadsOf(state).keySet().stream().map(Long::valueOf).toList())
                .orElse(List.of());
    }

    @Override
    public boolean applyLoadStatus(String driverId, Long loadId, String loadStatus) {
        return applyLoadStatus(driverId, loadId, loadStatus, null);
    }

    /**
     * Estado de una carga del conductor y, si el evento lo trae, su peso. Al subir a IN_TRANSIT la carga cuenta como
     * carga a bordo (netCargoWeightTons) y en el peso bruto; al entregarse o cancelarse deja de contar.
     */
    @Override
    public boolean applyLoadStatus(String driverId, Long loadId, String loadStatus, Double weightTons) {
        if (driverId == null || loadId == null || loadStatus == null) {
            return false;
        }
        DriverVehicleState state = repository.findById(driverId).orElse(null);
        if (state == null) {
            log.debug("Carga #{} ({}) ignorada: el conductor {} no tiene estado en la flota", loadId, loadStatus, driverId);
            return false;
        }
        Map<String, String> active = activeLoadsOf(state);
        Map<String, Double> weights = loadWeightsOf(state);
        String key = loadId.toString();
        boolean changed;
        if (COMMITTED_LOAD_STATUSES.contains(loadStatus)) {
            changed = !loadStatus.equals(active.put(key, loadStatus));
            if (weightTons != null && weightTons >= 0) {
                changed |= !weightTons.equals(weights.put(key, weightTons));
            }
        } else {
            changed = active.remove(key) != null;
            changed |= weights.remove(key) != null;
        }
        if (!changed) {
            return false;
        }
        state.setActiveLoads(active);
        state.setActiveLoadWeights(weights);
        double onBoard = onBoardTons(active, weights);
        state.setNetCargoWeightTons(onBoard);
        state.setGrossWeightTons(grossTons(state.getTareWeightTons(), onBoard));
        repository.save(state);
        log.info("Conductor {}: carga #{} -> {}; cargas activas {}; a bordo {} t", driverId, loadId, loadStatus, active, onBoard);
        return true;
    }

    private Map<String, String> activeLoadsOf(DriverVehicleState state) {
        return state.getActiveLoads() == null ? new HashMap<>() : new HashMap<>(state.getActiveLoads());
    }

    private Map<String, Double> loadWeightsOf(DriverVehicleState state) {
        return state.getActiveLoadWeights() == null ? new HashMap<>() : new HashMap<>(state.getActiveLoadWeights());
    }

    /** Toneladas a bordo: las cargas IN_TRANSIT con peso conocido (una carga aceptada aún no se ha subido). */
    static double onBoardTons(Map<String, String> activeLoads, Map<String, Double> weights) {
        double tons = activeLoads.entrySet().stream()
                .filter(e -> "IN_TRANSIT".equals(e.getValue()))
                .map(e -> weights.get(e.getKey()))
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        return Math.round(tons * 1000.0) / 1000.0;
    }

    private static boolean hasLoadOnBoard(Map<String, String> activeLoads, Map<String, Double> weights) {
        return activeLoads.entrySet().stream()
                .anyMatch(e -> "IN_TRANSIT".equals(e.getValue()) && weights.containsKey(e.getKey()));
    }

    private static double grossTons(Double tareTons, Double netCargoTons) {
        return (tareTons == null ? 0.0 : tareTons) + (netCargoTons == null ? 0.0 : netCargoTons);
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
                .maxPayloadTons(entity.getMaxPayloadTons())
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

    /** Placa colombiana de carga (ABC123, "abc 123", "ABC-123"): siempre "ABC-123". Otras, en mayúsculas y sin espacios al borde. */
    private static final java.util.regex.Pattern COLOMBIAN_PLATE = java.util.regex.Pattern.compile("^([A-Z]{3})[\\s-]*(\\d{2,3}[A-Z]?)$");

    static String normalizePlate(String plate) {
        if (plate == null) {
            return null;
        }
        String upper = plate.trim().toUpperCase(Locale.ROOT);
        java.util.regex.Matcher m = COLOMBIAN_PLATE.matcher(upper);
        return m.matches() ? m.group(1) + "-" + m.group(2) : upper;
    }
}
