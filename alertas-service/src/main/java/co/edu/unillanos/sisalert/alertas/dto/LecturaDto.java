package co.edu.unillanos.sisalert.alertas.dto;

import java.time.LocalDateTime;

/**
 * Objeto de transferencia para las mediciones históricas de una cuenca.
 */
public record LecturaDto(
        Long id,
        Long zonaId,
        Double precipitacionMm,
        LocalDateTime fechaHora,
        String fuente) {
}
