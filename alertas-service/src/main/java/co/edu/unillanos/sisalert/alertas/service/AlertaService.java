package co.edu.unillanos.sisalert.alertas.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unillanos.sisalert.alertas.dto.AlertaDto;
import co.edu.unillanos.sisalert.alertas.model.Alerta;
import co.edu.unillanos.sisalert.alertas.model.Lectura;
import co.edu.unillanos.sisalert.alertas.model.Zona;
import co.edu.unillanos.sisalert.alertas.repository.AlertaRepository;
import co.edu.unillanos.sisalert.alertas.repository.LecturaRepository;
import co.edu.unillanos.sisalert.alertas.repository.ZonaRepository;

@Service
public class AlertaService {

    private final ZonaRepository zonaRepository;
    private final AlertaRepository alertaRepository;
    private final LecturaRepository lecturaRepository;

    public AlertaService(ZonaRepository zonaRepository,
                         AlertaRepository alertaRepository,
                         LecturaRepository lecturaRepository) {
        this.zonaRepository = zonaRepository;
        this.alertaRepository = alertaRepository;
        this.lecturaRepository = lecturaRepository;
    }

    /** Una entrada por zona con su alerta vigente (o nulos si aún no hay datos). */
    @Transactional(readOnly = true)
    public List<AlertaDto> listarVigentes() {
        return zonaRepository.findAll(Sort.by("id")).stream().map(this::aDto).toList();
    }

    private AlertaDto aDto(Zona zona) {
        Alerta alerta = alertaRepository.findByZonaIdAndVigenteTrue(zona.getId())
                .stream().findFirst().orElse(null);
        if (alerta == null) {
            return new AlertaDto(zona.getId(), zona.getNombre(), zona.getCuerpoAgua(),
                    zona.getComunidades(), null, null, null, null);
        }
        Double mm = lecturaRepository.findFirstByZonaIdOrderByFechaHoraDescIdDesc(zona.getId())
                .map(Lectura::getPrecipitacionMm)
                .orElse(null);
        return new AlertaDto(zona.getId(), zona.getNombre(), zona.getCuerpoAgua(),
                zona.getComunidades(), alerta.getNivel(), alerta.getMensaje(), mm, alerta.getFechaHora());
    }
}