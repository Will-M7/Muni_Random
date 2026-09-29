package pe.gob.munisanmiguel.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.security.access.AccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    record ErrorResponse(String timestamp, int status, String error, String message, String path) {}
    @ExceptionHandler(NotFoundException.class) ResponseEntity<ErrorResponse> notFound(NotFoundException e, HttpServletRequest r) { return response(HttpStatus.NOT_FOUND, e.getMessage(), r); }
    @ExceptionHandler(ReniecNotConfiguredException.class) ResponseEntity<ErrorResponse> reniecNotConfigured(ReniecNotConfiguredException e, HttpServletRequest r) { return response(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage(), r); }
    @ExceptionHandler(ReniecUnavailableException.class) ResponseEntity<ErrorResponse> reniecUnavailable(ReniecUnavailableException e, HttpServletRequest r) { return response(HttpStatus.BAD_GATEWAY, e.getMessage(), r); }
    @ExceptionHandler(ConflictException.class) ResponseEntity<ErrorResponse> conflict(ConflictException e, HttpServletRequest r) { return response(HttpStatus.CONFLICT, e.getMessage(), r); }
    @ExceptionHandler(NoResourceFoundException.class) ResponseEntity<ErrorResponse> noResource(NoResourceFoundException e, HttpServletRequest r) { return response(HttpStatus.NOT_FOUND, "Recurso no encontrado.", r); }
    @ExceptionHandler(InvalidCredentialsException.class) ResponseEntity<ErrorResponse> unauthorized(InvalidCredentialsException e, HttpServletRequest r) { return response(HttpStatus.UNAUTHORIZED, e.getMessage(), r); }
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<ErrorResponse> denied(AccessDeniedException e, HttpServletRequest r) { return response(HttpStatus.FORBIDDEN, "No tienes permisos para esta operación.", r); }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class, HttpMessageNotReadableException.class}) ResponseEntity<ErrorResponse> bad(Exception e, HttpServletRequest r) {
        String message = e instanceof MethodArgumentNotValidException m
                ? m.getBindingResult().getFieldErrors().stream().map(x -> x.getField()+": "+x.getDefaultMessage()).collect(Collectors.joining("; "))
                : e instanceof IllegalArgumentException i && i.getMessage() != null && i.getMessage().startsWith("El DNI")
                    ? i.getMessage()
                    : "Solicitud inválida.";
        return response(HttpStatus.BAD_REQUEST, message == null ? "Solicitud inválida." : message, r);
    }
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class}) ResponseEntity<ErrorResponse> badParameter(Exception e, HttpServletRequest r) {
        return response(HttpStatus.BAD_REQUEST, "Parámetro de consulta inválido o faltante.", r);
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class) ResponseEntity<ErrorResponse> unsupported(HttpMediaTypeNotSupportedException e, HttpServletRequest r) { return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type no soportado.", r); }
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<ErrorResponse> tooLarge(MaxUploadSizeExceededException e, HttpServletRequest r) { return response(HttpStatus.PAYLOAD_TOO_LARGE, "El documento supera el tamaño máximo permitido.", r); }
    @ExceptionHandler(MissingServletRequestPartException.class) ResponseEntity<ErrorResponse> missingPart(MissingServletRequestPartException e, HttpServletRequest r) { return response(HttpStatus.BAD_REQUEST, "Debe adjuntar el documento PDF.", r); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class) ResponseEntity<ErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException e, HttpServletRequest r) { return response(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP no permitido para este recurso.", r); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ErrorResponse> integrityConflict(DataIntegrityViolationException e, HttpServletRequest r) { return response(HttpStatus.CONFLICT, "La operación entra en conflicto con datos existentes (por ejemplo, horario ocupado).", r); }
    @ExceptionHandler(Exception.class) ResponseEntity<ErrorResponse> generic(Exception e, HttpServletRequest r) { org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class).error("Unhandled API error", e); return response(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor.", r); }
    private ResponseEntity<ErrorResponse> response(HttpStatus status, String message, HttpServletRequest request) { return ResponseEntity.status(status).body(new ErrorResponse(OffsetDateTime.now().toString(), status.value(), status.getReasonPhrase(), message, request.getRequestURI())); }
}
