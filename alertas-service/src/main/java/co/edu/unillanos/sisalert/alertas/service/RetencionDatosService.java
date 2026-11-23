package co.edu.unillanos.sisalert.alertas.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unillanos.sisalert.alertas.repository.AlertaRepository;
import co.edu.unillanos.sisalert.alertas.repository.BoletinRepository;
import co.edu.unillanos.sisalert.alertas.repository.LecturaRepository;
import co.edu.unillanos.sisalert.alertas.repository.PrediccionRiesgoRepository;

/**
 * Política de retención de datos: el monitoreo automático guarda lecturas sin parar,
 * así que una vez al día se borran las que ya no aportan y las alertas históricas viejas.
 * La alerta vigente de cada zona nunca se borra.
 *
 * Configurable (en días) con alertas.retencion.lecturas-dias y alertas.retencion.alertas-dias;
 * con 0 o menos se desactiva esa limpieza.
 */
@Service
public class RetencionDatosService {

    private static final Logger log = LoggerFactory.getLogger(RetencionDatosService.class);

    private final LecturaRepository lecturaRepository;
    private final AlertaRepository alertaRepository;
    private final PrediccionRiesgoRepository prediccionRepository;
    private final BoletinRepository boletinRepository;
    private final int diasLecturas;
    private final int diasAlertas;
    private final Clock reloj;

    @Autowired
    public RetencionDatosService(
            LecturaRepository lecturaRepository,
            AlertaRepository alertaRepository,
            PrediccionRiesgoRepository prediccionRepository,
            BoletinRepository boletinRepository,
            @Value("${alertas.retencion.lecturas-dias:365}") int diasLecturas,
            @Value("${alertas.retencion.alertas-dias:730}") int diasAlertas) {
        this(lecturaRepository, alertaRepository, prediccionRepository, boletinRepository, diasLecturas,
                diasAlertas, Clock.systemDefaultZone());
    }

    RetencionDatosService(LecturaRepository lecturaRepository, AlertaRepository alertaRepository,
            PrediccionRiesgoRepository prediccionRepository, BoletinRepository boletinRepository,
            int diasLecturas, int diasAlertas, Clock reloj) {
        this.lecturaRepository = lecturaRepository;
        this.alertaRepository = alertaRepository;
        this.prediccionRepository = prediccionRepository;
        this.boletinRepository = boletinRepository;
        this.diasLecturas = diasLecturas;
        this.diasAlertas = diasAlertas;
        this.reloj = reloj;
    }

    /** Todos los días a las 3:00 a. m. (hora del servidor). */
    @Scheduled(cron = "${alertas.retencion.cron:0 0 3 * * *}")
    @Transactional
    public void limpiar() {
        LocalDateTime ahora = LocalDateTime.now(reloj);
        if (diasLecturas > 0) {
            int borradas = lecturaRepository.borrarAnterioresA(ahora.minusDays(diasLecturas));
            log.info("[RETENCIÓN] Lecturas de más de {} días borradas: {}", diasLecturas, borradas);
            // Las predicciones de IA se conservan lo mismo que las lecturas en que se basan.
            int predicciones = prediccionRepository.borrarAnterioresA(ahora.minusDays(diasLecturas));
            log.info("[RETENCIÓN] Predicciones de IA de más de {} días borradas: {}", diasLecturas, predicciones);
        }
        if (diasAlertas > 0) {
            int borradas = alertaRepository.borrarHistoricasAnterioresA(ahora.minusDays(diasAlertas));
            log.info("[RETENCIÓN] Alertas históricas de más de {} días borradas: {}", diasAlertas, borradas);
            int boletines = boletinRepository.borrarAnterioresA(ahora.minusDays(diasAlertas));
            log.info("[RETENCIÓN] Boletines de más de {} días borrados: {}", diasAlertas, boletines);
        }
    }
}
