package co.edu.unillanos.sisalert.alertas.exception;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(FuenteExternaException.class)
    public ResponseEntity<Map<String, String>> fuenteExterna(FuenteExternaException ex) {
        log.warn("Falló la fuente externa: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "FUENTE_EXTERNA_NO_DISPONIBLE",
                "mensaje", "No fue posible consultar la fuente de datos meteorológicos."));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> noEncontrado(RecursoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "NO_ENCONTRADO",
                "mensaje", ex.getMessage()));
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<Map<String, String>> conflicto(ConflictoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "CONFLICTO",
                "mensaje", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> solicitudInvalida(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() != null
                        ? error.getDefaultMessage()
                        : "Datos inválidos")
                .orElse("Datos inválidos");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "SOLICITUD_INVALIDA",
                "mensaje", mensaje));
    }
}
