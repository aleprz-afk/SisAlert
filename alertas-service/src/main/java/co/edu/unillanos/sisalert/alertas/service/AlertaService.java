package co.edu.unillanos.sisalert.alertas.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unillanos.sisalert.alertas.client.PrediccionIa;
import co.edu.unillanos.sisalert.alertas.dto.AlertaDto;
import co.edu.unillanos.sisalert.alertas.dto.RiesgoIaDto;
import co.edu.unillanos.sisalert.alertas.model.Alerta;
import co.edu.unillanos.sisalert.alertas.model.Lectura;
import co.edu.unillanos.sisalert.alertas.model.Zona;
import co.edu.unillanos.sisalert.alertas.repository.AlertaRepository;
import co.edu.unillanos.sisalert.alertas.repository.LecturaRepository;
import co.edu.unillanos.sisalert.alertas.repository.PrediccionRiesgoRepository;
import co.edu.unillanos.sisalert.alertas.repository.ZonaRepository;

@Service
public class AlertaService {

    private final ZonaRepository zonaRepository;
    private final AlertaRepository alertaRepository;
    private final LecturaRepository lecturaRepository;
    private final PrediccionRiesgoRepository prediccionRepository;

    private static final Logger log = LoggerFactory.getLogger(AlertaService.class);
    private static final TypeReference<List<PrediccionIa.Factor>> LISTA_FACTORES = new TypeReference<>() { };
    private final ObjectMapper mapper = new ObjectMapper();

    public AlertaService(ZonaRepository zonaRepository,
                         AlertaRepository alertaRepository,
                         LecturaRepository lecturaRepository,
                         PrediccionRiesgoRepository prediccionRepository) {
        this.zonaRepository = zonaRepository;
        this.alertaRepository = alertaRepository;
        this.lecturaRepository = lecturaRepository;
        this.prediccionRepository = prediccionRepository;
    }

    /** Una entrada por zona con su alerta vigente (o nulos si aún no hay datos). */
    @Transactional(readOnly = true)
    public List<AlertaDto> listarVigentes() {
        return zonaRepository.findAll(Sort.by("id")).stream().map(this::aDto).toList();
    }

    private AlertaDto aDto(Zona zona) {
        Alerta alerta = alertaRepository.findByZonaIdAndVigenteTrue(zona.getId())
                .stream().findFirst().orElse(null);
        RiesgoIaDto riesgoIa = riesgoIa(zona);
        if (alerta == null) {
            return new AlertaDto(zona.getId(), zona.getNombre(), zona.getCuerpoAgua(),
                    zona.getComunidades(), null, null, null, null, riesgoIa);
        }
        Double mm = lecturaRepository.findFirstByZonaIdOrderByFechaHoraDescIdDesc(zona.getId())
                .map(Lectura::getPrecipitacionMm)
                .orElse(null);
        return new AlertaDto(zona.getId(), zona.getNombre(), zona.getCuerpoAgua(),
                zona.getComunidades(), alerta.getNivel(), alerta.getMensaje(), mm, alerta.getFechaHora(), riesgoIa);
    }

    private RiesgoIaDto riesgoIa(Zona zona) {
        return prediccionRepository.findFirstByZonaIdOrderByFechaHoraDescIdDesc(zona.getId())
                .map(p -> new RiesgoIaDto(p.getRiesgo(), p.getProbabilidad(), leerFactores(p.getFactores()),
                        p.getVersionModelo(), p.getFechaHora()))
                .orElse(null);
    }

    private List<PrediccionIa.Factor> leerFactores(String json) {
        try {
            return mapper.readValue(json, LISTA_FACTORES);
        } catch (Exception e) {
            log.warn("Factores de IA ilegibles: {}", e.getMessage());
            return List.of();
        }
    }
}