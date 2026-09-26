package it.abc.musical.controllers.advice;

import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.exceptions.ConflictException;
import it.abc.musical.exceptions.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<Map<String, String>> badRequest(BadRequestException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Map<String, String>> conflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("error", "Dati non validi", "fields", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadableBody(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Corpo della richiesta non leggibile"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, String>> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Parametro '%s' non valido".formatted(ex.getName())));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<Map<String, String>> missingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Parametro '%s' obbligatorio".formatted(ex.getParameterName())));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, String>> resourceNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Risorsa non trovata"));
    }

    /** Vincolo del database violato: mai il testo SQL nella risposta, solo nel log. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> dataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Vincolo del database violato", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Operazione non valida: viola un vincolo del database"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> uploadTooLarge(MaxUploadSizeExceededException ex) {
        String message = ex.getMaxUploadSize() > 0
                ? "File troppo grande: supera il limite massimo di %d MB consentito dal server. "
                        .formatted(ex.getMaxUploadSize() / 1024 / 1024)
                        + "Riduci le dimensioni del file e riprova."
                : "File troppo grande. Riduci le dimensioni del file e riprova.";
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(Map.of("error", message));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> generic(Exception ex) {
        log.error("Errore non gestito", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Errore interno del server"));
    }
}
