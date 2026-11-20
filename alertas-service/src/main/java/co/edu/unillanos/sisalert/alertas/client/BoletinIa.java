package co.edu.unillanos.sisalert.alertas.client;

/**
 * Boletín redactado por ai-service.
 *
 * @param texto  mensaje para la comunidad
 * @param fuente "gemini" o "plantilla"
 * @param modelo modelo de Gemini usado (null si fue la plantilla)
 */
public record BoletinIa(String texto, String fuente, String modelo) { }
