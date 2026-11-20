package co.edu.unillanos.sisalert.alertas.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unillanos.sisalert.alertas.client.ClienteDatosExternos;
import co.edu.unillanos.sisalert.alertas.client.ClientePrediccionIa;
import co.edu.unillanos.sisalert.alertas.client.PrediccionIa;
import co.edu.unillanos.sisalert.alertas.dto.AlertaDto;
import co.edu.unillanos.sisalert.alertas.dto.LecturaDto;
import co.edu.unillanos.sisalert.alertas.exception.FuenteExternaException;
import co.edu.unillanos.sisalert.alertas.model.Alerta;
import co.edu.unillanos.sisalert.alertas.model.Lectura;
import co.edu.unillanos.sisalert.alertas.model.NivelAlerta;
import co.edu.unillanos.sisalert.alertas.model.PrediccionRiesgo;
import co.edu.unillanos.sisalert.alertas.model.Zona;
import co.edu.unillanos.sisalert.alertas.repository.AlertaRepository;
import co.edu.unillanos.sisalert.alertas.repository.LecturaRepository;
import co.edu.unillanos.sisalert.alertas.repository.PrediccionRiesgoRepository;
import co.edu.unillanos.sisalert.alertas.repository.ZonaRepository;
import co.edu.unillanos.sisalert.alertas.rules.ReglaDeUmbral;

@Service
public class SincronizacionService {

    private static final Logger log = LoggerFactory.getLogger(SincronizacionService.class);

    private record Medicion(Zona zona, double mm, Optional<PrediccionIa> prediccion) { }

    private final ZonaRepository zonaRepository;
    private final LecturaRepository lecturaRepository;
    private final AlertaRepository alertaRepository;
    private final ClienteDatosExternos cliente;
    private final ReglaDeUmbral regla;
    private final AlertaService alertaService;
    private final ClientePrediccionIa clienteIa;
    private final PrediccionRiesgoRepository prediccionRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public SincronizacionService(ZonaRepository zonaRepository,
                                 LecturaRepository lecturaRepository,
                                 AlertaRepository alertaRepository,
                                 ClienteDatosExternos cliente,
                                 ReglaDeUmbral regla,
                                 AlertaService alertaService,
                                 ClientePrediccionIa clienteIa,
                                 PrediccionRiesgoRepository prediccionRepository) {
        this.zonaRepository = zonaRepository;
        this.lecturaRepository = lecturaRepository;
        this.alertaRepository = alertaRepository;
        this.cliente = cliente;
        this.regla = regla;
        this.alertaService = alertaService;
        this.clienteIa = clienteIa;
        this.prediccionRepository = prediccionRepository;
    }

    /**
     * Consulta la fuente para todas las zonas y, solo si todo salió bien,
     * guarda las lecturas y reemplaza las alertas vigentes. Si algo falla,
     * lanza FuenteExternaException y no se modifica nada.
     *
     * Además, si el servicio de IA está disponible, guarda su predicción de riesgo para las
     * próximas 24 horas. La IA nunca frena la sincronización: si falla, la zona queda sin
     * predicción y la regla de umbral funciona igual.
     */
    @Transactional
    public List<AlertaDto> sincronizar() {
        List<Medicion> mediciones = new ArrayList<>();
        for (Zona zona : zonaRepository.findAll()) {
            double mm = cliente.precipitacionAcumuladaMm(zona.getCodigoEstacion());
            if (Double.isNaN(mm) || mm < 0) {
                throw new FuenteExternaException(
                        "Dato inválido para la estación " + zona.getCodigoEstacion() + ": " + mm);
            }
            mediciones.add(new Medicion(zona, mm, predecir(zona)));
        }

        LocalDateTime ahora = LocalDateTime.now();
        for (Medicion m : mediciones) {
            lecturaRepository.save(new Lectura(m.zona(), m.mm(), ahora, cliente.nombreFuente()));

            alertaRepository.findByZonaIdAndVigenteTrue(m.zona().getId())
                    .forEach(anterior -> anterior.setVigente(false));
            // Fuerza el UPDATE antes del INSERT siguiente: el índice único
            // ux_alerta_vigente admite una sola alerta vigente por zona.
            alertaRepository.flush();

            NivelAlerta nivel = regla.evaluar(m.mm());
            alertaRepository.save(new Alerta(m.zona(), nivel, regla.mensajePara(nivel), ahora));

            m.prediccion().ifPresent(p -> prediccionRepository.save(new PrediccionRiesgo(
                    m.zona(), ahora, p.probabilidad(), p.riesgo(), factoresJson(p), p.versionModelo())));
        }
        return alertaService.listarVigentes();
    }

    private Optional<PrediccionIa> predecir(Zona zona) {
        if (!clienteIa.habilitado()) {
            return Optional.empty();
        }
        Optional<PrediccionIa> prediccion = cliente.serieHoraria(zona.getCodigoEstacion()).flatMap(clienteIa::predecir);
        if (prediccion.isEmpty()) {
            log.warn("Zona {} sin predicción de IA en esta sincronización", zona.getNombre());
        }
        return prediccion;
    }

    private String factoresJson(PrediccionIa prediccion) {
        try {
            String json = mapper.writeValueAsString(prediccion.factores() == null ? List.of() : prediccion.factores());
            return json.length() <= 1000 ? json : "[]";
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * Consulta el historial reciente de lecturas (hasta las últimas 20)
     * registradas para una cuenca específica.
     */
    @Transactional(readOnly = true)
    public List<LecturaDto> obtenerHistorico(Long zonaId) {
        return lecturaRepository.findTop20ByZonaIdOrderByFechaHoraDesc(zonaId).stream()
                .map(l -> new LecturaDto(
                        l.getId(),
                        l.getZona().getId(),
                        l.getPrecipitacionMm(),
                        l.getFechaHora(),
                        l.getFuente()))
                .toList();
    }
}