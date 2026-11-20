package co.edu.unillanos.sisalert.alertas.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unillanos.sisalert.alertas.exception.FuenteExternaException;

/**
 * Cliente meteorológico en tiempo real (Open-Meteo API).
 * Consulta datos verídicos de satélites y modelos hidrometeorológicos (WMO / ECMWF / NOAA)
 * para las coordenadas exactas de cada cuenca en Villavicencio.
 *
 * Se activa con alertas.fuente=open-meteo en application.properties.
 */
@Component
@ConditionalOnProperty(name = "alertas.fuente", havingValue = "open-meteo")
public class ClienteOpenMeteo implements ClienteDatosExternos {

    private static final Logger log = LoggerFactory.getLogger(ClienteOpenMeteo.class);

    private record Coordenada(double lat, double lon) {}

    // Coordenadas geográficas exactas de los sectores urbanos de riesgo en Villavicencio (POMCA)
    private static final Map<String, Coordenada> COORDENADAS = Map.of(
            "0035035100", new Coordenada(4.1485, -73.6340), // Guatiquía Norte (Barzal / Siete de Agosto)
            "3502700090", new Coordenada(4.0750, -73.6550), // Guayuriba Sur (Catumare / Porfía)
            "0035025110", new Coordenada(4.1280, -73.6260)  // Caño Grande / Maizaro (Villacentro / Porvenir)
    );

    private final HttpClient http;
    private final ObjectMapper mapper;

    public ClienteOpenMeteo() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public String nombreFuente() {
        return "OPEN-METEO (En Vivo)";
    }

    @Override
    public double precipitacionAcumuladaMm(String codigoEstacion) throws FuenteExternaException {
        Coordenada coord = COORDENADAS.get(codigoEstacion);
        if (coord == null) {
            // Coordenadas por defecto centro de Villavicencio
            coord = new Coordenada(4.1420, -73.6266);
        }

        // Consulta precipitación acumulada del día en tiempo real (timezone de Colombia)
        // Se usa Locale.US para asegurar el punto decimal en la URL en vez de coma
        String url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&daily=precipitation_sum&timezone=America%%2FBogota",
                coord.lat(), coord.lon());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "SisAlert-Weather/1.0")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.error("Open-Meteo error HTTP {}: {}", resp.statusCode(), resp.body());
                throw new FuenteExternaException("Open-Meteo respondió HTTP " + resp.statusCode() + " para URL: " + url);
            }

            double lluviaMm = leerPrecipitacionHoy(mapper.readTree(resp.body()), codigoEstacion);
            log.info("Estación {} (lat {}, lon {}) · Precipitación hoy: {} mm",
                    codigoEstacion, coord.lat(), coord.lon(), lluviaMm);
            return lluviaMm;

        } catch (FuenteExternaException e) {
            throw e;
        } catch (Exception e) {
            throw new FuenteExternaException("Error al consultar datos meteorológicos reales de " + codigoEstacion, e);
        }
    }

    /**
     * Extrae la lluvia de hoy (primer elemento de daily.precipitation_sum).
     * Un valor ausente o nulo es un dato faltante, no "0 mm": se reporta como falla de la
     * fuente para no mostrar una zona en NORMAL sin tener datos que lo respalden.
     */
    static double leerPrecipitacionHoy(JsonNode root, String codigoEstacion) throws FuenteExternaException {
        JsonNode dailyPrecip = root.path("daily").path("precipitation_sum");
        if (!dailyPrecip.isArray() || dailyPrecip.isEmpty()) {
            throw new FuenteExternaException("Respuesta sin datos diarios para estación " + codigoEstacion);
        }
        JsonNode hoy = dailyPrecip.get(0);
        if (!hoy.isNumber()) {
            throw new FuenteExternaException("Open-Meteo no reportó lluvia de hoy para estación " + codigoEstacion);
        }
        double lluviaMm = hoy.asDouble();
        if (lluviaMm < 0) {
            throw new FuenteExternaException("Valor de lluvia inválido (" + lluviaMm + ") para estación " + codigoEstacion);
        }
        return Math.round(lluviaMm * 10.0) / 10.0;
    }
}
