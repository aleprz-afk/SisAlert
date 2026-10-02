package co.edu.unillanos.sisalert.alertas.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unillanos.sisalert.alertas.dto.AlertaDto;
import co.edu.unillanos.sisalert.alertas.service.SincronizacionService;

@RestController
@RequestMapping("/api/lecturas")
public class LecturaController {

    private final SincronizacionService sincronizacionService;

    public LecturaController(SincronizacionService sincronizacionService) {
        this.sincronizacionService = sincronizacionService;
    }

    @PostMapping("/sincronizar")
    public List<AlertaDto> sincronizar() {
        return sincronizacionService.sincronizar();
    }
}