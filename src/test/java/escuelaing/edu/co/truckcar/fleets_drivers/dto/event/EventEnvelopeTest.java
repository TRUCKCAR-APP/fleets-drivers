package escuelaing.edu.co.truckcar.fleets_drivers.dto.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeTest {

    @Test
    void serializesDriverAvailabilityEventEnvelope() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        EventEnvelope<VehicleTelemetryEvent> envelope = EventEnvelope.<VehicleTelemetryEvent>builder()
                .eventId(eventId)
                .eventType("driver.availability.changed")
                .occurredAt(Instant.parse("2026-10-10T23:00:00Z"))
                .correlationId(correlationId)
                .version(1)
                .producer("fleets-drivers")
                .payload(VehicleTelemetryEvent.builder()
                        .driverId("driver-42")
                        .vehiclePlate("ABC123")
                        .status("AVAILABLE")
                        .remainingDrivingHours(7.5)
                        .build())
                .build();

        EventEnvelope<VehicleTelemetryEvent> restored = mapper.readValue(
                mapper.writeValueAsString(envelope),
                mapper.getTypeFactory().constructParametricType(
                        EventEnvelope.class,
                        VehicleTelemetryEvent.class
                )
        );

        assertThat(restored.getEventId()).isEqualTo(eventId);
        assertThat(restored.getEventType()).isEqualTo("driver.availability.changed");
        assertThat(restored.getOccurredAt()).isEqualTo(Instant.parse("2026-10-10T23:00:00Z"));
        assertThat(restored.getCorrelationId()).isEqualTo(correlationId);
        assertThat(restored.getVersion()).isEqualTo(1);
        assertThat(restored.getProducer()).isEqualTo("fleets-drivers");
        assertThat(restored.getPayload().getDriverId()).isEqualTo("driver-42");
        assertThat(restored.getPayload().getStatus()).isEqualTo("AVAILABLE");
    }
}
