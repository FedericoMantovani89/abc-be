package it.abc.musical.controllers.advice;

import it.abc.musical.config.Messages;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.exceptions.CodedException;
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

/**
 * Ogni risposta di errore e' {"error": "<messaggio italiano>", "code": "<codice stabile>"}
 * (piu' "fields" per la validazione): il frontend legge il codice, il messaggio resta per
 * compatibilita'. I codici sono elencati in docs/codici-errore.md.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(NotFoundException ex) {
        return coded(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<Map<String, String>> badRequest(BadRequestException ex) {
        return coded(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<Map<String, String>> conflict(ConflictException ex) {
        return coded(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        String code = "errore.dati.non.validi";
        return ResponseEntity.badRequest()
                .body(Map.of("error", Messages.text(code), "code", code, "fields", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadableBody(HttpMessageNotReadableException ex) {
        return plain(HttpStatus.BAD_REQUEST, "errore.corpo.non.leggibile");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Map<String, String>> typeMismatch(MethodArgumentTypeMismatchException ex) {
        return plain(HttpStatus.BAD_REQUEST, "errore.parametro.non.valido", ex.getName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<Map<String, String>> missingParameter(MissingServletRequestParameterException ex) {
        return plain(HttpStatus.BAD_REQUEST, "errore.parametro.obbligatorio", ex.getParameterName());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, String>> resourceNotFound(NoResourceFoundException ex) {
        return plain(HttpStatus.NOT_FOUND, "errore.risorsa.non.trovata");
    }

    /** Vincolo del database violato: mai il testo SQL nella risposta, solo nel log. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> dataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Vincolo del database violato", ex);
        return plain(HttpStatus.CONFLICT, "errore.vincolo.database");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> uploadTooLarge(MaxUploadSizeExceededException ex) {
        return ex.getMaxUploadSize() > 0
                ? plain(HttpStatus.CONTENT_TOO_LARGE, "errore.file.troppo.grande.limite",
                        String.valueOf(ex.getMaxUploadSize() / 1024 / 1024))
                : plain(HttpStatus.CONTENT_TOO_LARGE, "errore.file.troppo.grande");
    }

    /** Ultima rete: nessun dettaglio interno nella risposta (lo stack trace va solo nel log). */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> generic(Exception ex) {
        log.error("Errore non gestito", ex);
        return plain(HttpStatus.INTERNAL_SERVER_ERROR, "errore.interno");
    }

    private static ResponseEntity<Map<String, String>> coded(HttpStatus status, CodedException ex) {
        return ResponseEntity.status(status).body(Map.of("error", ex.getMessage(), "code", ex.getCode()));
    }

    private static ResponseEntity<Map<String, String>> plain(HttpStatus status, String code, Object... args) {
        return ResponseEntity.status(status).body(Map.of("error", Messages.text(code, args), "code", code));
    }
}
