package co.edu.unillanos.sisalert.alertas.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unillanos.sisalert.alertas.dto.AlertaDto;
import co.edu.unillanos.sisalert.alertas.service.AlertaService;

@RestController
@RequestMapping("/api/alertas")
public class AlertaController {

    private final AlertaService alertaService;

    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @GetMapping
    public List<AlertaDto> vigentes() {
        return alertaService.listarVigentes();
    }
}