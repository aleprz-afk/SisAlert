package co.edu.unillanos.sisalert.alertas.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import co.edu.unillanos.sisalert.alertas.model.Zona;
import co.edu.unillanos.sisalert.alertas.repository.ZonaRepository;

/** Carga las zonas de ejemplo al arrancar (la base H2 en memoria empieza vacía). */
@Component
public class CargaInicial implements CommandLineRunner {

    private final ZonaRepository zonaRepository;

    public CargaInicial(ZonaRepository zonaRepository) {
        this.zonaRepository = zonaRepository;
    }

    @Override
    public void run(String... args) {
        if (zonaRepository.count() > 0) {
            return;
        }
        // PROVISIONAL: confirmar zonas, comunidades y estaciones con Eduar
        zonaRepository.saveAll(List.of(
                new Zona("Zona Guatiquía", "Río Guatiquía", "Por confirmar", "EST-001"),
                new Zona("Zona Caño Parrado", "Caño Parrado", "Por confirmar", "EST-002"),
                new Zona("Zona Caño Maizaro", "Caño Maizaro", "Por confirmar", "EST-003"),
                new Zona("Zona Río Ocoa", "Río Ocoa", "Por confirmar", "EST-004")));
    }
}