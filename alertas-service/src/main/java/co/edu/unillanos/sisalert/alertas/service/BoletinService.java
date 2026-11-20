package co.edu.unillanos.sisalert.alertas.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import co.edu.unillanos.sisalert.alertas.client.ClientePrediccionIa;
import co.edu.unillanos.sisalert.alertas.client.PrediccionIa;
import co.edu.unillanos.sisalert.alertas.dto.BoletinDto;
import co.edu.unillanos.sisalert.alertas.model.BoletinZona;
import co.edu.unillanos.sisalert.alertas.model.NivelAlerta;
import co.edu.unillanos.sisalert.alertas.model.Zona;
import co.edu.unillanos.sisalert.alertas.repository.BoletinRepository;

/**
 * Boletines para la comunidad (IA generativa con Gemini, vía ai-service).
 *
 * Para no gastar llamadas, un boletín nuevo solo se pide cuando CAMBIA el estado de la zona
 * (nivel del semáforo o banda de riesgo de la IA). Las zonas en NORMAL no tienen boletín.
 * Si el último boletín salió de la plantilla (Gemini sin clave, sin cuota o caído), se vuelve
 * a intentar como máximo cada REINTENTO_PLANTILLA.
 *
 * En la simulación del administrador los boletines se guardan solo en memoria, una vez por
 * zona y estado, para no llamar a Gemini en cada actualización de la pantalla.
 */
@Service
public class BoletinService {

    static final Duration REINTENTO_PLANTILLA = Duration.ofMinutes(30);

    private final BoletinRepository boletinRepository;
    private final ClientePrediccionIa clienteIa;
    private final Map<String, BoletinDto> cacheSimulacion = new ConcurrentHashMap<>();

    public BoletinService(BoletinRepository boletinRepository, ClientePrediccionIa clienteIa) {
        this.boletinRepository = boletinRepository;
        this.clienteIa = clienteIa;
    }

    static String claveEstado(NivelAlerta nivel, PrediccionIa prediccion) {
        return nivel.name() + "|" + (prediccion == null ? "SIN_IA" : prediccion.riesgo());
    }

    /** Se llama en cada sincronización real, después de guardar la alerta de la zona. */
    public void actualizar(Zona zona, NivelAlerta nivel, double mm, PrediccionIa prediccion, LocalDateTime ahora) {
        if (nivel == NivelAlerta.NORMAL || !clienteIa.habilitado()) {
            return;
        }
        String clave = claveEstado(nivel, prediccion);
        Optional<BoletinZona> ultimo = boletinRepository.findFirstByZonaIdOrderByFechaHoraDescIdDesc(zona.getId());
        boolean mismoEstado = ultimo.map(b -> b.getClaveEstado().equals(clave)).orElse(false);
        boolean reintentarPlantilla = ultimo
                .map(b -> "plantilla".equals(b.getFuente())
                        && b.getFechaHora().isBefore(ahora.minus(REINTENTO_PLANTILLA)))
                .orElse(false);
        if (mismoEstado && !reintentarPlantilla) {
            return;
        }
        clienteIa.redactarBoletin(datosParaBoletin(zona, nivel, mm, prediccion))
                .ifPresent(b -> boletinRepository.save(new BoletinZona(zona, clave, b.texto(), b.fuente(), ahora)));
    }

    /** Boletín vigente de la zona, si corresponde a su nivel actual. */
    public BoletinDto vigente(Zona zona, NivelAlerta nivelActual) {
        if (nivelActual == null || nivelActual == NivelAlerta.NORMAL) {
            return null;
        }
        return boletinRepository.findFirstByZonaIdOrderByFechaHoraDescIdDesc(zona.getId())
                .filter(b -> b.getClaveEstado().startsWith(nivelActual.name() + "|"))
                .map(b -> new BoletinDto(b.getTexto(), b.getFuente(), b.getFechaHora()))
                .orElse(null);
    }

    /** Boletín para la simulación del administrador: solo en memoria, una vez por zona y estado. */
    public BoletinDto simulado(Zona zona, NivelAlerta nivel, double mm, PrediccionIa prediccion, String escenario) {
        if (nivel == NivelAlerta.NORMAL || !clienteIa.habilitado()) {
            return null;
        }
        String clave = escenario + "|" + zona.getId() + "|" + claveEstado(nivel, prediccion);
        BoletinDto enCache = cacheSimulacion.get(clave);
        if (enCache != null) {
            return enCache;
        }
        return clienteIa.redactarBoletin(datosParaBoletin(zona, nivel, mm, prediccion))
                .map(b -> {
                    BoletinDto dto = new BoletinDto(b.texto(), b.fuente(), LocalDateTime.now());
                    // Solo se guardan en caché los de Gemini: si salió la plantilla, se reintenta luego.
                    if ("gemini".equals(b.fuente())) {
                        cacheSimulacion.put(clave, dto);
                    }
                    return dto;
                })
                .orElse(null);
    }

    /** Solo datos estructurados del sistema: Gemini nunca recibe texto libre de usuarios. */
    static Map<String, Object> datosParaBoletin(Zona zona, NivelAlerta nivel, double mm, PrediccionIa prediccion) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("zona", zona.getNombre());
        datos.put("cuerpoAgua", zona.getCuerpoAgua());
        datos.put("comunidades", zona.getComunidades());
        datos.put("nivel", nivel.name());
        datos.put("precipitacionMm", mm);
        if (prediccion != null) {
            List<Map<String, String>> factores = prediccion.factores() == null ? List.of()
                    : prediccion.factores().stream()
                            .limit(5)
                            .map(f -> Map.of("descripcion", f.descripcion(), "efecto", f.efecto()))
                            .toList();
            datos.put("riesgoIa", Map.of(
                    "riesgo", prediccion.riesgo(),
                    "probabilidad", prediccion.probabilidad(),
                    "factores", factores));
        }
        return datos;
    }
}
