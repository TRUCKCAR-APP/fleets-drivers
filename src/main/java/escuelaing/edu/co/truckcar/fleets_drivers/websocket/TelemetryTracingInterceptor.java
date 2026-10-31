package escuelaing.edu.co.truckcar.fleets_drivers.websocket;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Una traza por cada posición que llega por STOMP (SEND /app/telemetry). Los frames no pasan por la instrumentación
 * HTTP, así que sin esto el trabajo del controlador (Redis, histórico, retransmisión) no tendría traceId en los logs
 * ni en Tempo. El X-Correlation-Id del frame o, si no lo trae, el del handshake (lo pone el api-gateway) se propaga
 * como baggage a las llamadas y eventos que salgan de esa traza.
 * <p>
 * Solo se observa el handler de los @MessageMapping: el canal de entrada entrega cada frame también al broker y al
 * resolvedor de destinos de usuario, y cada uno abriría su propio span.
 */
@Component
public class TelemetryTracingInterceptor implements ExecutorChannelInterceptor {

    static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final ObservationRegistry observationRegistry;
    private final ObjectProvider<Tracer> tracer;
    private final ThreadLocal<Active> current = new ThreadLocal<>();

    public TelemetryTracingInterceptor(ObjectProvider<ObservationRegistry> observationRegistry,
                                       ObjectProvider<Tracer> tracer) {
        this.observationRegistry = observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP);
        this.tracer = tracer;
    }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        if (!(handler instanceof SimpAnnotationMethodMessageHandler)) {
            return message;
        }
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SEND) {
            return message;
        }
        String destination = accessor.getDestination() == null ? "none" : accessor.getDestination();
        Observation observation = Observation.createNotStarted("truckcar.telemetry.inbound", observationRegistry)
                .contextualName("stomp SEND " + destination)
                .lowCardinalityKeyValue("destination", destination)
                .start();
        Observation.Scope scope = observation.openScope();
        String correlationId = correlationIdOf(accessor);
        BaggageInScope baggage = null;
        Tracer activeTracer = tracer.getIfAvailable();
        if (correlationId != null && activeTracer != null) {
            baggage = activeTracer.createBaggageInScope(CORRELATION_HEADER, correlationId);
            MDC.put(CORRELATION_HEADER, correlationId);
        }
        current.set(new Active(observation, scope, baggage));
        return message;
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler,
                                    Exception exception) {
        if (!(handler instanceof SimpAnnotationMethodMessageHandler)) {
            return;
        }
        Active active = current.get();
        if (active == null) {
            return;
        }
        current.remove();
        if (active.baggage() != null) {
            active.baggage().close();
            MDC.remove(CORRELATION_HEADER);
        }
        active.scope().close();
        if (exception != null) {
            active.observation().error(exception);
        }
        active.observation().stop();
    }

    private static String correlationIdOf(StompHeaderAccessor accessor) {
        String fromFrame = accessor.getFirstNativeHeader(CORRELATION_HEADER);
        if (fromFrame != null && !fromFrame.isBlank()) {
            return fromFrame;
        }
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object fromHandshake = attributes == null ? null : attributes.get(GatewayIdentityHandshakeInterceptor.CORRELATION_ATTRIBUTE);
        return fromHandshake instanceof String value && !value.isBlank() ? value : null;
    }

    private record Active(Observation observation, Observation.Scope scope, BaggageInScope baggage) {
    }
}
