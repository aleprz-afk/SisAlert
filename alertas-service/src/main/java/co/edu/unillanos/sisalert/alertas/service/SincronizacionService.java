package co.edu.unillanos.sisalert.alertas.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unillanos.sisalert.alertas.client.ClienteDatosExternos;
import co.edu.unillanos.sisalert.alertas.dto.AlertaDto;
import co.edu.unillanos.sisalert.alertas.exception.FuenteExternaException;
import co.edu.unillanos.sisalert.alertas.model.Alerta;
import co.edu.unillanos.sisalert.alertas.model.Lectura;
import co.edu.unillanos.sisalert.alertas.model.NivelAlerta;
import co.edu.unillanos.sisalert.alertas.model.Zona;
import co.edu.unillanos.sisalert.alertas.repository.AlertaRepository;
import co.edu.unillanos.sisalert.alertas.repository.LecturaRepository;
import co.edu.unillanos.sisalert.alertas.repository.ZonaRepository;
import co.edu.unillanos.sisalert.alertas.rules.ReglaDeUmbral;

@Service
public class SincronizacionService {

    private record Medicion(Zona zona, double mm) { }

    private final ZonaRepository zonaRepository;
    private final LecturaRepository lecturaRepository;
    private final AlertaRepository alertaRepository;
    private final ClienteDatosExternos cliente;
    private final ReglaDeUmbral regla;
    private final AlertaService alertaService;

    public SincronizacionService(ZonaRepository zonaRepository,
                                 LecturaRepository lecturaRepository,
                                 AlertaRepository alertaRepository,
                                 ClienteDatosExternos cliente,
                                 ReglaDeUmbral regla,
                                 AlertaService alertaService) {
        this.zonaRepository = zonaRepository;
        this.lecturaRepository = lecturaRepository;
        this.alertaRepository = alertaRepository;
        this.cliente = cliente;
        this.regla = regla;
        this.alertaService = alertaService;
    }

    /**
     * Consulta la fuente para todas las zonas y, solo si todo salió bien,
     * guarda las lecturas y reemplaza las alertas vigentes. Si algo falla,
     * lanza FuenteExternaException y no se modifica nada.
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
            mediciones.add(new Medicion(zona, mm));
        }

        LocalDateTime ahora = LocalDateTime.now();
        for (Medicion m : mediciones) {
            lecturaRepository.save(new Lectura(m.zona(), m.mm(), ahora, cliente.nombreFuente()));

            alertaRepository.findByZonaIdAndVigenteTrue(m.zona().getId())
                    .forEach(anterior -> anterior.setVigente(false));

            NivelAlerta nivel = regla.evaluar(m.mm());
            alertaRepository.save(new Alerta(m.zona(), nivel, regla.mensajePara(nivel), ahora));
        }
        return alertaService.listarVigentes();
    }
}