package co.edu.unillanos.sisalert.alertas.config;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import co.edu.unillanos.sisalert.alertas.model.Zona;
import co.edu.unillanos.sisalert.alertas.repository.ZonaRepository;

/**
 * Carga las zonas de riesgo la primera vez que arranca el servicio (la tabla
 * zonas está vacía). Si la base ya tiene zonas, no hace nada: los datos
 * persisten entre reinicios.
 *
 * Las tres zonas corresponden a las estaciones de precipitación del IDEAM
 * con cobertura en Villavicencio (conjunto s54a-sgyg, datos.gov.co).
 *
 * Cada zona agrupa los cuerpos de agua que drenan hacia la misma cuenca
 * y comparten la estación de referencia más cercana. Las comunidades son
 * los barrios con mayor exposición a inundación según el POMCA del
 * río Guatiquía (CORMACARENA, 2018).
 */
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
        zonaRepository.saveAll(List.of(

            /*
             * Zona 1 — Cuenca del río Guatiquía (norte y oriente de la ciudad)
             * Estación: ICA Villavicencio (0035035100) · lat 4.137 · lng -73.625
             * Cuerpos de agua: río Guatiquía, caño Parrado, caño Gramalote,
             *                  quebrada Grande, quebrada Honda, caño Buque.
             */
            new Zona(
                "Zona Guatiquía Norte",
                "Río Guatiquía · Caño Parrado · Caño Gramalote · Quebrada Grande · Quebrada Honda · Caño Buque",
                "Barzal Alto, Barzal Bajo, Siete de Agosto, Villa Lorena, Mancera, La Nohora, El Triunfo",
                "0035035100"
            ),

            /*
             * Zona 2 — Cuenca del río Guayuriba (sur de la ciudad)
             * Estación: Guayuriba (3502700090) · lat 3.958 · lng -73.271
             * Cuerpos de agua: río Guayuriba, caño Rosablanca, caño La Cuerera.
             */
            new Zona(
                "Zona Guayuriba Sur",
                "Río Guayuriba · Caño Rosablanca · Caño La Cuerera",
                "Apiay, Catumare, Playa Rica, La Esperanza, La Nohora Rural, Vanguardia",
                "3502700090"
            ),

            /*
             * Zona 3 — Caño Grande y sistema Maizaro (centro y sur-oriente)
             * Estación: La Libertad (0035025110) · lat 4.057 · lng -73.468
             * Cuerpos de agua: caño Grande, caño Maizaro, quebrada La Unión.
             */
            new Zona(
                "Zona Caño Grande Centro",
                "Caño Grande · Caño Maizaro · Quebrada La Unión",
                "Porvenir, Bello Horizonte, Villacentro, La Rosita, Marsella, Porfía",
                "0035025110"
            )
        ));
    }
}
