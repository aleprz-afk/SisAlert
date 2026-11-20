package co.edu.unillanos.sisalert.alertas.client;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unillanos.sisalert.alertas.exception.FuenteExternaException;

/**
 * Cliente real que consulta precipitación del IDEAM a través del portal de
 * datos abiertos de Colombia (datos.gov.co, conjunto s54a-sgyg).
 *
 * Se activa con alertas.fuente=ideam en application.properties.
 * Si la fuente no responde o devuelve datos inválidos lanza FuenteExternaException
 * y el servicio conserva las alertas anteriores (responde 502 al frontend).
 *
 * Estrategia de fecha: el portal puede acumular varios días de retraso.
 * Se busca la ventana de 24 h más reciente disponible para cada estación
 * y se suma la precipitación de todos los intervalos dentro de esa ventana.
 * La fecha del dato se registra en la Lectura para que la pantalla la muestre.
 */
@Component
@ConditionalOnProperty(name = "alertas.fuente", havingValue = "ideam")
public class ClienteIdeam implements ClienteDatosExternos {

    private static final Logger log = LoggerFactory.getLogger(ClienteIdeam.class);

    private static final String BASE_URL =
            "https://www.datos.gov.co/resource/s54a-sgyg.json";
    private static final String CODIGO_SENSOR = "0240";   // precipitación
    private static final int TIMEOUT_SEGUNDOS = 25;

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String appToken;

    public ClienteIdeam(
            @Value("${alertas.ideam.app-token:}") String appToken) {
        this.appToken = appToken;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SEGUNDOS))
                .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public String nombreFuente() {
        return "IDEAM";
    }

    /**
     * Suma la precipitación acumulada (mm) de la ventana de 24 h más reciente
     * que tenga datos para la estación indicada.
     */
    @Override
    public double precipitacionAcumuladaMm(String codigoEstacion)
            throws FuenteExternaException {

        // 1. Buscar la fecha del dato más reciente para esta estación
        LocalDate fechaReciente = fechaMasReciente(codigoEstacion);

        // 2. Sumar todos los intervalos de ese día
        return sumarPrecipitacionDia(codigoEstacion, fechaReciente);
    }

    // -------------------------------------------------------------------------
    // Pasos internos
    // -------------------------------------------------------------------------

    /**
     * Pide el registro más reciente de la estación para saber en qué fecha
     * están los últimos datos disponibles.
     */
    private LocalDate fechaMasReciente(String codigoEstacion)
            throws FuenteExternaException {

        String altCodigo = codigoEstacion.startsWith("00")
                ? codigoEstacion.substring(2)
                : "00" + codigoEstacion;

        String soql = "$select=max(fechaobservacion)"
                + "&$where=" + encode("(codigoestacion='" + codigoEstacion
                + "' OR codigoestacion='" + altCodigo + "') AND codigosensor='" + CODIGO_SENSOR + "'");
        String url = BASE_URL + "?" + soql;

        String cuerpo = get(url);
        try {
            JsonNode raiz = mapper.readTree(cuerpo);
            if (!raiz.isArray() || raiz.isEmpty()) {
                throw new FuenteExternaException(
                        "Sin datos para la estación " + codigoEstacion);
            }
            String fechaTexto = raiz.get(0).path("max_fechaobservacion").asText("");
            if (fechaTexto.isBlank()) {
                throw new FuenteExternaException(
                        "Fecha vacía en la respuesta para " + codigoEstacion);
            }
            // El portal devuelve "2026-07-24T23:50:00.000"
            return LocalDate.parse(fechaTexto.substring(0, 10),
                    DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (FuenteExternaException e) {
            throw e;
        } catch (Exception e) {
            throw new FuenteExternaException(
                    "Error al parsear la fecha más reciente de " + codigoEstacion, e);
        }
    }

    /**
     * Descarga todos los registros de la estación en la fecha indicada
     * y suma los valores de precipitación del día.
     */
    private double sumarPrecipitacionDia(String codigoEstacion, LocalDate fecha)
            throws FuenteExternaException {

        String inicio = fecha + "T00:00:00.000";
        String fin    = fecha + "T23:59:59.999";

        String altCodigo = codigoEstacion.startsWith("00")
                ? codigoEstacion.substring(2)
                : "00" + codigoEstacion;

        String soql = "$where=" + encode(
                "(codigoestacion='" + codigoEstacion + "' OR codigoestacion='" + altCodigo + "')"
                + " AND codigosensor='" + CODIGO_SENSOR + "'"
                + " AND fechaobservacion >= '" + inicio + "'"
                + " AND fechaobservacion <= '" + fin + "'")
                + "&$select=fechaobservacion,valorobservado"
                + "&$order=fechaobservacion ASC"
                + "&$limit=200";

        String url = BASE_URL + "?" + soql;
        String cuerpo = get(url);

        try {
            JsonNode registros = mapper.readTree(cuerpo);
            if (!registros.isArray()) {
                throw new FuenteExternaException(
                        "Respuesta inesperada del portal para " + codigoEstacion);
            }

            double total = 0.0;
            for (JsonNode r : registros) {
                String valorTexto = r.path("valorobservado").asText("0");
                try {
                    double v = Double.parseDouble(valorTexto);
                    if (v >= 0) {
                        total += v;
                    }
                } catch (NumberFormatException ignored) {
                    log.warn("Valor no numérico ignorado en estación {}: {}",
                            codigoEstacion, valorTexto);
                }
            }

            double totalRedondeado = Math.round(total * 10.0) / 10.0;
            log.info("Estación {} · {} · {} registros · total {} mm",
                    codigoEstacion, fecha, registros.size(), totalRedondeado);
            return totalRedondeado;

        } catch (FuenteExternaException e) {
            throw e;
        } catch (Exception e) {
            throw new FuenteExternaException(
                    "Error al parsear datos de la estación " + codigoEstacion, e);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP
    // -------------------------------------------------------------------------

    private String get(String url) throws FuenteExternaException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT_SEGUNDOS))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SisAlert-IdeamClient/1.0")
                    .header("Accept", "application/json")
                    .GET();
            if (!appToken.isBlank()) {
                builder.header("X-App-Token", appToken);
            }
            HttpResponse<String> resp = http.send(
                    builder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new FuenteExternaException(
                        "El portal respondió HTTP " + resp.statusCode()
                        + " para la URL: " + url);
            }
            return resp.body();
        } catch (FuenteExternaException e) {
            throw e;
        } catch (Exception e) {
            throw new FuenteExternaException(
                    "No se pudo conectar con datos.gov.co", e);
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
