package escuelaing.edu.co.truckdar.fleets_drivers.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import escuelaing.edu.co.truckdar.fleets_drivers.dto.event.EventEnvelope;
import escuelaing.edu.co.truckdar.fleets_drivers.dto.event.VehicleTelemetryEvent;
import escuelaing.edu.co.truckdar.fleets_drivers.model.DriverVehicleState;
import escuelaing.edu.co.truckdar.fleets_drivers.model.OutboxEvent;
import escuelaing.edu.co.truckdar.fleets_drivers.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void enqueueAvailabilityEvent(DriverVehicleState state) {
        VehicleTelemetryEvent payload = VehicleTelemetryEvent.builder()
                .driverId(state.getDriverId())
                .vehiclePlate(state.getVehiclePlate())
                .latitude(state.getCurrentLatitude())
                .longitude(state.getCurrentLongitude())
                .speedKmH(state.getCurrentSpeedKmH())
                .remainingDrivingHours(state.getRemainingDrivingHours())
                .status(state.getStatus())
                .timestamp(state.getLastUpdate())
                .build();

        EventEnvelope<VehicleTelemetryEvent> envelope = EventEnvelope.<VehicleTelemetryEvent>builder()
                .eventId(UUID.randomUUID())
                .eventType("driver.availability.changed")
                .occurredAt(state.getLastUpdate().atZone(java.time.ZoneId.systemDefault()).toInstant())
                .correlationId(UUID.nameUUIDFromBytes(state.getDriverId().getBytes(StandardCharsets.UTF_8)))
                .version(1)
                .producer("fleets-drivers")
                .payload(payload)
                .build();

        try {
            repository.save(OutboxEvent.builder()
                    .id(envelope.getEventId())
                    .aggregateType("DriverVehicleState")
                    .aggregateId(state.getDriverId())
                    .eventType(envelope.getEventType())
                    .payload(objectMapper.writeValueAsString(envelope))
                    .occurredAt(envelope.getOccurredAt())
                    .createdAt(Instant.now())
                    .attempts(0)
                    .build());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar el evento de disponibilidad para Outbox", exception);
        }
    }
}
