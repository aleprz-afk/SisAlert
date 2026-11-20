package co.edu.unillanos.sisalert.alertas.client;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fuente de demostración con resultados fijos (no aleatorios).
 *
 * Sirve para mostrar el comportamiento del sistema con lluvia aunque sea época seca.
 * Se activa con alertas.fuente=escenario y se elige el escenario con alertas.escenario:
 *   seca      -> todas las zonas en NORMAL
 *   tormenta  -> Guatiquía Norte en PRECAUCION, Guayuriba Sur en PELIGRO, Caño Grande Centro en NORMAL
 *   creciente -> todas las zonas en PELIGRO
 *
 * Los milímetros se calculan a partir de los umbrales configurados, así el escenario
 * sigue siendo coherente aunque se cambien los umbrales.
 */
@Component
@ConditionalOnProperty(name = "alertas.fuente", havingValue = "escenario")
public class ClienteEscenario implements ClienteDatosExternos {

    static final String GUATIQUIA_NORTE = "0035035100";
    static final String GUAYURIBA_SUR = "3502700090";

    private static final List<String> ESCENARIOS = List.of("seca", "tormenta", "creciente");

    private final String escenario;
    private final double mmNormal;
    private final double mmPrecaucion;
    private final double mmPeligro;

    public ClienteEscenario(
            @Value("${alertas.escenario:tormenta}") String escenario,
            @Value("${alertas.umbral.precaucion-mm}") double umbralPrecaucion,
            @Value("${alertas.umbral.peligro-mm}") double umbralPeligro) {
        String elegido = escenario.trim().toLowerCase(Locale.ROOT);
        if (!ESCENARIOS.contains(elegido)) {
            throw new IllegalArgumentException(
                    "alertas.escenario debe ser uno de " + ESCENARIOS + ", se recibió: " + escenario);
        }
        this.escenario = elegido;
        this.mmNormal = redondear(umbralPrecaucion * 0.3);
        this.mmPrecaucion = redondear((umbralPrecaucion + umbralPeligro) / 2);
        this.mmPeligro = redondear(umbralPeligro * 1.15);
    }

    @Override
    public String nombreFuente() {
        return "ESCENARIO (" + escenario + ")";
    }

    @Override
    public double precipitacionAcumuladaMm(String codigoEstacion) {
        return switch (escenario) {
            case "seca" -> mmNormal;
            case "creciente" -> mmPeligro;
            default -> switch (codigoEstacion) {
                case GUATIQUIA_NORTE -> mmPrecaucion;
                case GUAYURIBA_SUR -> mmPeligro;
                default -> mmNormal;
            };
        };
    }

    private static double redondear(double mm) {
        return Math.round(mm * 10.0) / 10.0;
    }
}
