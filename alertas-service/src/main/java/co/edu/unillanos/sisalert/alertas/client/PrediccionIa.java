package co.edu.unillanos.sisalert.alertas.client;

import java.util.List;

/**
 * Respuesta del servicio de IA para una zona.
 *
 * @param probabilidad  P(lluvia >= 40 mm en las próximas 24 h), entre 0 y 1
 * @param riesgo        BAJO | MEDIO | ALTO
 * @param factores      variables que más pesaron en la predicción
 * @param versionModelo versión del modelo que respondió
 */
public record PrediccionIa(double probabilidad, String riesgo, List<Factor> factores, String versionModelo) {

    /** @param efecto "aumenta" o "disminuye" el riesgo */
    public record Factor(String descripcion, String efecto, double aporte) { }
}
