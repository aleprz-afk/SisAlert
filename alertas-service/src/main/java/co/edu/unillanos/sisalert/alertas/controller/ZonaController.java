package co.edu.unillanos.sisalert.alertas.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unillanos.sisalert.alertas.dto.ZonaDto;
import co.edu.unillanos.sisalert.alertas.dto.ZonaRequest;
import co.edu.unillanos.sisalert.alertas.service.ZonaService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/zonas")
public class ZonaController {

    private final ZonaService zonaService;

    public ZonaController(ZonaService zonaService) {
        this.zonaService = zonaService;
    }

    @GetMapping
    public List<ZonaDto> listar() {
        return zonaService.listar();
    }

    @GetMapping("/{id}")
    public ZonaDto obtener(@PathVariable Long id) {
        return zonaService.obtener(id);
    }

    @PostMapping
    public ResponseEntity<ZonaDto> crear(@Valid @RequestBody ZonaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(zonaService.crear(request));
    }

    @PutMapping("/{id}")
    public ZonaDto actualizar(@PathVariable Long id, @Valid @RequestBody ZonaRequest request) {
        return zonaService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        zonaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
