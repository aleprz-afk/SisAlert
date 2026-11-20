package co.edu.unillanos.sisalert.alertas.client;

import java.util.Map;

/**
 * Coordenadas de los sectores urbanos de riesgo de Villavicencio (POMCA) asociadas a cada
 * código de estación. Las usan las consultas a Open-Meteo (lluvia del día y serie horaria).
 * Las mismas coordenadas están en ai-service/training/descargar_datos.py.
 */
public final class CoordenadasEstaciones {

    public record Coordenada(double lat, double lon) { }

    private static final Map<String, Coordenada> POR_ESTACION = Map.of(
            "0035035100", new Coordenada(4.1485, -73.6340), // Guatiquía Norte (Barzal / Siete de Agosto)
            "3502700090", new Coordenada(4.0750, -73.6550), // Guayuriba Sur (Catumare / Porfía)
            "0035025110", new Coordenada(4.1280, -73.6260)  // Caño Grande / Maizaro (Villacentro / Porvenir)
    );

    /** Centro de Villavicencio: se usa para zonas creadas desde el CRUD sin coordenadas propias. */
    private static final Coordenada CENTRO = new Coordenada(4.1420, -73.6266);

    private CoordenadasEstaciones() { }

    public static Coordenada de(String codigoEstacion) {
        return POR_ESTACION.getOrDefault(codigoEstacion, CENTRO);
    }
}
