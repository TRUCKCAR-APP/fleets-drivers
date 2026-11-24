package escuelaing.edu.co.truckcar.fleets_drivers.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;

/**
 * Errores en formato RFC 7807 (ProblemDetail): type, title, status, detail e instance (la ruta de la petición).
 * <p>
 * Para no romper a los clientes actuales se conservan como propiedades adicionales los campos de antes: message (el
 * texto que muestra la PWA), error (= title), details (errores por campo en un 400) y timestamp. Content-Type:
 * application/problem+json para quien lo pida en Accept y application/json para el resto (la PWA envía Accept:
 * application/json y decide por ese tipo si interpreta el cuerpo como JSON).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String TYPE_BASE = "https://truckcar.co/errors/";

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Recurso no encontrado", ex.getMessage());
    }

    @ExceptionHandler(PlateAlreadyRegisteredException.class)
    public ResponseEntity<ProblemDetail> handlePlateConflict(PlateAlreadyRegisteredException ex) {
        return problem(HttpStatus.CONFLICT, "plate-already-registered", "Placa ya registrada", ex.getMessage());
    }

    @ExceptionHandler(FleetAccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(FleetAccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Acceso denegado", ex.getMessage());
    }

    @ExceptionHandler(FleetUnauthenticatedException.class)
    public ResponseEntity<ProblemDetail> handleUnauthenticated(FleetUnauthenticatedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthenticated", "Usuario no autenticado", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new TreeMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        String detail = fieldErrors.isEmpty() ? "La solicitud tiene datos inválidos" : String.join("; ", fieldErrors.values());
        ResponseEntity<ProblemDetail> response = problem(HttpStatus.BAD_REQUEST, "validation-error", "Validación fallida", detail);
        response.getBody().setProperty("details", fieldErrors);
        // La misma información como lista (el formato habitual de RFC 7807); details se conserva para la PWA.
        response.getBody().setProperty("errors", fieldErrors.entrySet().stream()
                .map(e -> Map.of("field", e.getKey(), "message", e.getValue())).toList());
        return response;
    }

    /** JSON mal formado, UTF-8 inválido o un valor que no corresponde al tipo (enum desconocido): error del cliente. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.debug("Cuerpo ilegible: {}", ex.getMessage());
        // Sin el detalle del parser en la respuesta: puede incluir fragmentos del cuerpo y nombres de clases internas.
        return problem(HttpStatus.BAD_REQUEST, "unreadable-body", "Cuerpo de la solicitud inválido",
                "El cuerpo debe ser JSON válido en UTF-8 con los campos y valores esperados");
    }

    @ExceptionHandler(IdentityUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleIdentityUnavailable(IdentityUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable", "Servicio no disponible", ex.getMessage());
    }

    @ExceptionHandler(LoadAllocationUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleLoadAllocationUnavailable(LoadAllocationUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "service-unavailable", "Servicio no disponible", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-parameter", "Parámetro inválido",
                ex.getName() + ": valor con un tipo incorrecto");
    }

    /** El detalle queda en el log: al cliente no se le exponen clases ni mensajes internos. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex) {
        // Excepciones de Spring MVC con su propio estado (405, 415, parámetro faltante...): este handler genérico las
        // capturaría antes que el resolver por defecto y las convertiría en 500.
        if (ex instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            return problem(errorResponse.getStatusCode(), "bad-request", "Solicitud inválida",
                    errorResponse.getBody().getDetail());
        }
        log.error("Excepción interna no controlada: ", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Error interno",
                "No se pudo procesar la solicitud de la flota");
    }

    /** ProblemDetail con los campos de antes (message, error, timestamp) para no romper a los clientes actuales. */
    static ResponseEntity<ProblemDetail> problem(HttpStatusCode status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + type));
        problem.setTitle(title);
        problem.setProperty("error", title);
        problem.setProperty("message", detail);
        problem.setProperty("timestamp", LocalDateTime.now());
        return ResponseEntity.status(status).contentType(negotiatedType()).body(problem);
    }

    /**
     * application/problem+json solo para quien lo pide en Accept. Spring lo usaría también ante Accept:
     * application/json, y la PWA trataría el cuerpo como texto (decide por "application/json" en el Content-Type).
     */
    private static MediaType negotiatedType() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String accept = attributes.getRequest().getHeader("Accept");
            if (accept != null && accept.contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE)) {
                return MediaType.APPLICATION_PROBLEM_JSON;
            }
        }
        return MediaType.APPLICATION_JSON;
    }
}
