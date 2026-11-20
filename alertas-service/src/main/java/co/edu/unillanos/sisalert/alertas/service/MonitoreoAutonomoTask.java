package co.edu.unillanos.sisalert.alertas.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.edu.unillanos.sisalert.alertas.dto.AlertaDto;
import co.edu.unillanos.sisalert.alertas.exception.FuenteExternaException;

import java.util.List;

/**
 * Tarea en segundo plano para el Monitoreo Autónomo 24/7 de SisAlert.
 *
 * Consulta periódicamente la fuente meteorológica, evalúa los umbrales
 * y actualiza la base de datos sin necesidad de intervención humana.
 *
 * Configurable en application.properties:
 * - alertas.monitoreo-automatico.habilitado=true (activar/desactivar)
 * - alertas.monitoreo-automatico.intervalo-ms=60000 (frecuencia en milisegundos)
 */
@Component
@ConditionalOnProperty(name = "alertas.monitoreo-automatico.habilitado", havingValue = "true", matchIfMissing = true)
public class MonitoreoAutonomoTask {

    private static final Logger log = LoggerFactory.getLogger(MonitoreoAutonomoTask.class);

    private final SincronizacionService sincronizacionService;

    public MonitoreoAutonomoTask(SincronizacionService sincronizacionService) {
        this.sincronizacionService = sincronizacionService;
    }

    /**
     * Se ejecuta periódicamente en segundo plano.
     * initialDelay = 10000: espera 10s después del arranque para dar tiempo
     * a que la base de datos y la red estén 100% listas.
     */
    @Scheduled(
        fixedRateString = "${alertas.monitoreo-automatico.intervalo-ms:60000}",
        initialDelay = 10000
    )
    public void ejecutarMonitoreoPeriodico() {
        log.info("[MONITOREO AUTÓNOMO] Iniciando escaneo periódico de estaciones...");
        try {
            List<AlertaDto> alertas = sincronizacionService.sincronizar();
            long enPeligro = alertas.stream().filter(a -> "PELIGRO".equals(String.valueOf(a.nivel()))).count();
            long enPrecaucion = alertas.stream().filter(a -> "PRECAUCION".equals(String.valueOf(a.nivel()))).count();

            log.info("[MONITOREO AUTÓNOMO] Escaneo completado. {} cuencas evaluadas (Peligro: {}, Precaución: {}).",
                    alertas.size(), enPeligro, enPrecaucion);

            if (enPeligro > 0) {
                log.warn("[ALERTA CRÍTICA] ¡Se detectaron cuencas en nivel de PELIGRO por lluvias torrenciales!");
            }
        } catch (FuenteExternaException e) {
            log.warn("[MONITOREO AUTÓNOMO] No fue posible actualizar en esta pasada: {}", e.getMessage());
        } catch (Exception e) {
            log.error("[MONITOREO AUTÓNOMO] Error inesperado en escaneo automático: {}", e.getMessage(), e);
        }
    }
}
