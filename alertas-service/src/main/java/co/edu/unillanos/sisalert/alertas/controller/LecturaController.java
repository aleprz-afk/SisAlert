package co.edu.unillanos.sisalert.alertas.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unillanos.sisalert.alertas.dto.AlertaDto;
import co.edu.unillanos.sisalert.alertas.dto.LecturaDto;
import co.edu.unillanos.sisalert.alertas.service.SincronizacionService;

@RestController
@RequestMapping("/api/lecturas")
public class LecturaController {

    private static final Logger log = LoggerFactory.getLogger(LecturaController.class);

    private final SincronizacionService sincronizacionService;

    public LecturaController(SincronizacionService sincronizacionService) {
        this.sincronizacionService = sincronizacionService;
    }

    @PostMapping("/sincronizar")
    public List<AlertaDto> sincronizar(Authentication usuario) {
        // El usuario ya viene validado por Spring Security (firma, vencimiento y rol Admin).
        log.info("Sincronización solicitada por: {}", usuario.getName());
        return sincronizacionService.sincronizar();
    }

    @GetMapping("/zonas/{zonaId}/historico")
    public List<LecturaDto> obtenerHistorico(@PathVariable Long zonaId) {
        return sincronizacionService.obtenerHistorico(zonaId);
    }
}