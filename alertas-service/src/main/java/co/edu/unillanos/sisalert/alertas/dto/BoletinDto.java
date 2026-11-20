package co.edu.unillanos.sisalert.alertas.dto;

import java.time.LocalDateTime;

/**
 * Boletín para la comunidad de una zona en alerta.
 *
 * @param texto     mensaje en lenguaje sencillo
 * @param fuente    "gemini" (IA generativa) o "plantilla" (mensaje fijo de respaldo)
 * @param fechaHora cuándo se redactó
 */
public record BoletinDto(String texto, String fuente, LocalDateTime fechaHora) {
}
