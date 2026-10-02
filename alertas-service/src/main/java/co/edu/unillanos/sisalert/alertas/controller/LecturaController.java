package co.edu.unillanos.sisalert.alertas.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unillanos.sisalert.alertas.dto.AlertaDto;
import co.edu.unillanos.sisalert.alertas.security.UsuarioToken;
import co.edu.unillanos.sisalert.alertas.service.SincronizacionService;

@RestController
@RequestMapping("/api/lecturas")
public class LecturaController {

    private static final Logger log = LoggerFactory.getLogger(LecturaController.class);

    private final SincronizacionService sincronizacionService;
    private final UsuarioToken usuarioToken;

    public LecturaController(SincronizacionService sincronizacionService, UsuarioToken usuarioToken) {
        this.sincronizacionService = sincronizacionService;
        this.usuarioToken = usuarioToken;
    }

    @PostMapping("/sincronizar")
    public List<AlertaDto> sincronizar(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        log.info("Sincronización solicitada por: {}", usuarioToken.extraer(authorization));
        return sincronizacionService.sincronizar();
    }
}