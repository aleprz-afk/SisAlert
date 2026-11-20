package co.edu.unillanos.sisalert.alertas.dto;

import java.time.LocalDateTime;
import java.util.List;

import co.edu.unillanos.sisalert.alertas.client.PrediccionIa;

/**
 * Última predicción del servicio de IA para una zona.
 *
 * @param riesgo        BAJO | MEDIO | ALTO (riesgo de lluvia >= 40 mm en las próximas 24 h)
 * @param probabilidad  entre 0 y 1
 * @param factores      variables que más pesaron, con su efecto (aumenta / disminuye)
 * @param versionModelo versión del modelo que la calculó
 * @param fechaHora     momento de la sincronización en que se calculó
 */
public record RiesgoIaDto(
        String riesgo,
        double probabilidad,
        List<PrediccionIa.Factor> factores,
        String versionModelo,
        LocalDateTime fechaHora) {
}
