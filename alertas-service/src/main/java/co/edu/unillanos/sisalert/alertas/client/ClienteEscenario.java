package co.edu.unillanos.sisalert.alertas.client;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
 *
 * También entrega una serie horaria sintética coherente con cada nivel (lluvia de la semana y
 * pronóstico), para que el modelo de IA "vea" la misma tormenta durante la demostración.
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
    private final double umbralPrecaucion;
    private final double umbralPeligro;

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
        this.umbralPrecaucion = umbralPrecaucion;
        this.umbralPeligro = umbralPeligro;
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

    /**
     * Serie sintética: la lluvia del día se reparte en las últimas 12 horas; antes de una
     * zona en peligro llovió también los dos días previos (suelo saturado), y el pronóstico
     * sigue la misma tendencia que el nivel de la zona.
     */
    @Override
    public Optional<SerieLluvia> serieHoraria(String codigoEstacion) {
        double mmHoy = precipitacionAcumuladaMm(codigoEstacion);
        boolean peligro = mmHoy >= umbralPeligro;
        boolean precaucion = !peligro && mmHoy >= umbralPrecaucion;

        List<Double> pasadas = new ArrayList<>(Collections.nCopies(SerieLluvia.HORAS_HISTORIA, 0.0));
        int ultima = SerieLluvia.HORAS_HISTORIA - 1;
        double previa = peligro ? 0.5 : precaucion ? 0.3 : 0.0;
        for (int h = ultima - 72 + 1; h <= ultima - 24; h++) {
            pasadas.set(h, previa);
        }
        for (int h = ultima - 12 + 1; h <= ultima; h++) {
            pasadas.set(h, redondear(mmHoy / 12));
        }
        double porHora = peligro ? 4.0 : precaucion ? 1.5 : 0.0;
        List<Double> pronostico = Collections.nCopies(SerieLluvia.HORAS_PRONOSTICO, porHora);
        LocalDateTime ahora = LocalDateTime.now(ZoneId.of("America/Bogota")).truncatedTo(ChronoUnit.HOURS);
        return Optional.of(new SerieLluvia(pasadas, pronostico, ahora));
    }

    private static double redondear(double mm) {
        return Math.round(mm * 10.0) / 10.0;
    }
}
