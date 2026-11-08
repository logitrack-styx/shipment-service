package com.logitrack.shipment.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

/**
 * Traduce las excepciones del negocio y de Spring a las respuestas HTTP
 * definidas en el contrato 2.8 del plan de trabajo.
 *
 * <p>Centraliza tanto las excepciones propias (404 / 409) como los errores
 * de Bean Validation, JSON mal formado y conversión de parámetros (400).
 * Las respuestas 500 nunca exponen detalles internos ni stack traces.</p>
 *
 * @author TheMiggaCool
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * @param ex falla controlada de integración con identidad
     * @param request petición fallida
     * @return 503 sin detalles de infraestructura
     */
    @ExceptionHandler(IdentityIntegrationException.class)
    public ResponseEntity<ErrorResponse> handleIdentity(IdentityIntegrationException ex,
            HttpServletRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        return build(
                HttpStatus.BAD_REQUEST,
                "Los datos de la solicitud son inválidos");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccesoDenegado(
            AccessDeniedException ex,
            HttpServletRequest request) {

        ErrorResponse respuesta = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .mensaje("No tiene permisos para realizar esta operación")
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(respuesta);
    }

    /**
     * Gestiona los casos en los que el recurso solicitado no existe.
     *
     * @param ex excepción que describe el recurso ausente
     * @param request petición que originó el error
     * @return respuesta HTTP 404
     */
    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> handleRecursoNoEncontrado(
            RecursoNoEncontradoException ex,
            HttpServletRequest request) {

        ErrorResponse respuesta = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                OffsetDateTime.now(ZoneOffset.UTC));

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(respuesta);
    }

    /**
     * Gestiona los conflictos causados por recursos duplicados.
     *
     * @param ex excepción del recurso duplicado
     * @param request petición que originó el error
     * @return respuesta HTTP 409
     */
    @ExceptionHandler(RecursoYaExistenteException.class)
    public ResponseEntity<ErrorResponse> handleRecursoYaExistente(
            RecursoYaExistenteException ex,
            HttpServletRequest request) {

        ErrorResponse respuesta = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                OffsetDateTime.now(ZoneOffset.UTC));

        return ResponseEntity.status(HttpStatus.CONFLICT).body(respuesta);
    }

    /**
     * Gestiona las transiciones de estado no permitidas.
     *
     * @param ex excepción de transición inválida
     * @param request petición que originó el error
     * @return respuesta HTTP 409
     */
    @ExceptionHandler(TransicionEstadoInvalidaException.class)
    public ResponseEntity<ErrorResponse> handleTransicionEstadoInvalido(
            TransicionEstadoInvalidaException ex,
            HttpServletRequest request) {

        ErrorResponse respuesta = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                OffsetDateTime.now(ZoneOffset.UTC));

        return ResponseEntity.status(HttpStatus.CONFLICT).body(respuesta);
    }

    /**
     * Gestiona errores de validación sobre el cuerpo de la petición.
     *
     * @param ex excepción que contiene los errores de validación
     * @param request petición que originó el error
     * @return respuesta HTTP 400 con los campos inválidos
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleArgumentoInvalido(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String mensaje = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error ->
                        error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return build(
                HttpStatus.BAD_REQUEST,
                mensaje.isBlank()
                        ? "Datos de entrada inválidos"
                        : mensaje);
    }

    /**
     * Gestiona violaciones de restricciones en parámetros individuales.
     *
     * @param ex excepción con las restricciones incumplidas
     * @param request petición que originó el error
     * @return respuesta HTTP 400
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleViolacionDeRestriccion(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        String mensaje = ex.getConstraintViolations()
                .stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));

        return build(
                HttpStatus.BAD_REQUEST,
                mensaje.isBlank()
                        ? "Datos de entrada inválidos"
                        : mensaje);
    }

    /**
     * Gestiona cuerpos JSON ilegibles o mal formados.
     *
     * @param ex excepción de conversión del cuerpo
     * @param request petición que originó el error
     * @return respuesta HTTP 400
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleJsonInvalido(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        return build(
                HttpStatus.BAD_REQUEST,
                "El cuerpo de la petición es ilegible o tiene un formato JSON inválido");
    }

    /**
     * Gestiona UUID, enumeraciones u otros tipos de parámetros inválidos.
     *
     * @param ex excepción de conversión del parámetro
     * @param request petición que originó el error
     * @return respuesta HTTP 400
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTipoDeArgumentoInvalido(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        return build(
                HttpStatus.BAD_REQUEST,
                "El parámetro '" + ex.getName()
                        + "' tiene un formato inválido");
    }

    /**
     * Gestiona conflictos con restricciones de integridad de la base de datos.
     *
     * @param ex excepción de integridad
     * @param request petición que originó el error
     * @return respuesta HTTP 409
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegridadDeDatos(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {

        return build(
                HttpStatus.CONFLICT,
                "La operación viola una restricción de integridad de datos");
    }

    /**
     * Informa un conflicto cuando otra transacción modificó la misma entidad.
     *
     * @param ex conflicto de bloqueo optimista traducido por Spring
     * @return respuesta HTTP 409 sin detalles internos de persistencia
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConflictoConcurrente(
            OptimisticLockingFailureException ex) {
        return build(HttpStatus.CONFLICT,
                "El envío fue modificado por otra solicitud; vuelva a consultar");
    }

    /**
     * Gestiona errores internos no contemplados por otros manejadores.
     *
     * <p>No expone detalles internos ni trazas de ejecución al cliente.</p>
     *
     * @param ex excepción no controlada
     * @param request petición que originó el error
     * @return respuesta HTTP 500
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleExceptionGlobal(
            Exception ex,
            HttpServletRequest request) {

        ErrorResponse response = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Ocurrió un error interno inesperado. Por favor intente más tarde.",
                OffsetDateTime.now(ZoneOffset.UTC));

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    /**
     * Construye una respuesta de error con el formato común del servicio.
     *
     * @param status estado HTTP
     * @param mensaje descripción del error
     * @return respuesta HTTP con el DTO de error
     */
    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            String mensaje) {

        ErrorResponse respuesta = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .mensaje(mensaje)
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        return ResponseEntity.status(status).body(respuesta);
    }

    /**
     * Traduce los errores de integración con courier-service al estado HTTP
     * definido por el contrato técnico.
     *
     * @param ex excepción con el estado HTTP y el mensaje controlado
     * @param request petición HTTP que originó el error
     * @return respuesta con el formato común de errores
     */
    @ExceptionHandler(CourierIntegrationException.class)
    public ResponseEntity<ErrorResponse> handleCourierIntegration(
            CourierIntegrationException ex,
            HttpServletRequest request) {

        HttpStatus status = ex.getStatus();

        ErrorResponse respuesta = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .mensaje(ex.getMessage())
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        return ResponseEntity.status(status).body(respuesta);
    }
}
