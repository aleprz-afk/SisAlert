package co.edu.unillanos.sisalert.alertas.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Cliente del servicio de IA (ai-service).
 *
 * La IA complementa la regla de umbral, no la reemplaza: si el servicio no está configurado,
 * no responde o responde con error, se devuelve Optional.empty() y la sincronización sigue
 * normalmente con la regla de umbral.
 */
@Component
public class ClientePrediccionIa {

    private static final Logger log = LoggerFactory.getLogger(ClientePrediccionIa.class);

    private final String url;
    private final String apiKey;
    private final Duration timeout;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ClientePrediccionIa(
            @Value("${alertas.ia.url:}") String url,
            @Value("${alertas.ia.api-key:}") String apiKey,
            @Value("${alertas.ia.timeout-ms:5000}") long timeoutMs) {
        this.url = url == null ? "" : url.strip().replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.timeout = Duration.ofMillis(timeoutMs);
        // HTTP/1.1 explícito: por defecto el cliente de Java intenta "subir" la conexión a
        // HTTP/2 (h2c) y Uvicorn no lo admite, con lo que el cuerpo de la petición se pierde.
        this.http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(this.timeout)
                .build();
        if (this.url.isEmpty()) {
            log.warn("Servicio de IA no configurado (AI_SERVICE_URL vacío): solo se usará la regla de umbral.");
        } else {
            log.info("Servicio de IA: {}", this.url);
        }
    }

    public boolean habilitado() {
        return !url.isEmpty();
    }

    public Optional<PrediccionIa> predecir(SerieLluvia serie) {
        if (!habilitado()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("serieHorariaMm", serie.pasadasMm());
            cuerpo.put("pronosticoHorarioMm", serie.pronosticoMm());
            cuerpo.put("fechaHora", serie.horaActual().toString());

            HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(url + "/predict"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(cuerpo)));
            if (!apiKey.isEmpty()) {
                peticion.header("X-Api-Key", apiKey);
            }

            HttpResponse<String> respuesta = http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                log.warn("El servicio de IA respondió HTTP {}: {}", respuesta.statusCode(), respuesta.body());
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(respuesta.body(), PrediccionIa.class));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("No fue posible consultar el servicio de IA: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
