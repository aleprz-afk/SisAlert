package co.edu.unillanos.sisalert.alertas.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter HORA_OPEN_METEO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

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
        CoordenadasEstaciones.Coordenada coord = CoordenadasEstaciones.de(codigoEstacion);

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
     * Lluvia horaria de los últimos 7 días y pronóstico de las próximas 24 h en una sola consulta
     * (past_days=7, forecast_days=2). Es la misma fuente con la que se entrenó el modelo de IA.
     */
    @Override
    public Optional<SerieLluvia> serieHoraria(String codigoEstacion) {
        CoordenadasEstaciones.Coordenada coord = CoordenadasEstaciones.de(codigoEstacion);
        String url = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&hourly=precipitation"
                        + "&past_days=7&forecast_days=2&timezone=America%%2FBogota",
                coord.lat(), coord.lon());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "SisAlert-Weather/1.0")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                log.warn("Open-Meteo (serie horaria) respondió HTTP {} para {}", resp.statusCode(), codigoEstacion);
                return Optional.empty();
            }
            LocalDateTime horaActual = LocalDateTime.now(BOGOTA).truncatedTo(ChronoUnit.HOURS);
            return armarSerie(mapper.readTree(resp.body()), horaActual);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("No se pudo obtener la serie horaria de {}: {}", codigoEstacion, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Toma de la respuesta horaria las 168 horas que terminan en horaActual y las 24 siguientes.
     * Si falta alguna hora o algún valor, no hay serie (la zona queda sin predicción de IA).
     */
    static Optional<SerieLluvia> armarSerie(JsonNode root, LocalDateTime horaActual) {
        JsonNode tiempos = root.path("hourly").path("time");
        JsonNode lluvia = root.path("hourly").path("precipitation");
        if (!tiempos.isArray() || !lluvia.isArray() || tiempos.size() != lluvia.size()) {
            return Optional.empty();
        }
        int actual = -1;
        for (int i = 0; i < tiempos.size(); i++) {
            if (LocalDateTime.parse(tiempos.get(i).asText(), HORA_OPEN_METEO).equals(horaActual)) {
                actual = i;
                break;
            }
        }
        int desde = actual - SerieLluvia.HORAS_HISTORIA + 1;
        int hasta = actual + SerieLluvia.HORAS_PRONOSTICO;
        if (actual < 0 || desde < 0 || hasta >= lluvia.size()) {
            return Optional.empty();
        }
        List<Double> pasadas = new ArrayList<>();
        List<Double> pronostico = new ArrayList<>();
        for (int i = desde; i <= hasta; i++) {
            JsonNode valor = lluvia.get(i);
            if (!valor.isNumber() || valor.asDouble() < 0) {
                return Optional.empty();
            }
            (i <= actual ? pasadas : pronostico).add(valor.asDouble());
        }
        return Optional.of(new SerieLluvia(pasadas, pronostico, horaActual));
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
