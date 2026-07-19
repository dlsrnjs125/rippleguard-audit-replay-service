package dev.rippleguard.audit.interfaces.rest;

import dev.rippleguard.audit.application.NotFoundException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {
    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(NotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 404,
                "error", "Not Found",
                "message", exception.getMessage()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", 400,
                "error", "Bad Request",
                "message", exception.getMessage()
        ));
    }
}
