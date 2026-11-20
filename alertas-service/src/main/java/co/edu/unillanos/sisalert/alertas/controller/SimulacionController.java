package co.edu.unillanos.sisalert.alertas.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unillanos.sisalert.alertas.dto.AlertaDto;
import co.edu.unillanos.sisalert.alertas.service.SimulacionService;

/** Simulación de escenarios de lluvia. Solo Admin (ver SeguridadConfig). No guarda nada. */
@RestController
@RequestMapping("/api/simulacion")
public class SimulacionController {

    private static final Logger log = LoggerFactory.getLogger(SimulacionController.class);

    private final SimulacionService simulacionService;

    public SimulacionController(SimulacionService simulacionService) {
        this.simulacionService = simulacionService;
    }

    @GetMapping
    public List<String> escenarios() {
        return simulacionService.escenarios();
    }

    @GetMapping("/{escenario}")
    public List<AlertaDto> simular(@PathVariable String escenario, Authentication usuario) {
        log.debug("Simulación '{}' consultada por {}", escenario, usuario.getName());
        return simulacionService.simular(escenario);
    }
}
