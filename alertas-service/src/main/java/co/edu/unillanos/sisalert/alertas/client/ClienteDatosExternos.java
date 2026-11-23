package co.edu.unillanos.sisalert.alertas.client;

import java.util.Optional;

import co.edu.unillanos.sisalert.alertas.exception.FuenteExternaException;

/**
 * Contrato para consultar la fuente meteorológica (IDEAM u otra).
 * Cualquier fuente nueva implementa esta interfaz sin tocar el resto del servicio.
 */
public interface ClienteDatosExternos {

    /** Nombre de la fuente; se guarda en cada Lectura. */
    String nombreFuente();

    /** Lluvia acumulada del día (mm) de la estación. Lanza FuenteExternaException si falla. */
    double precipitacionAcumuladaMm(String codigoEstacion) throws FuenteExternaException;

    /**
     * Lluvia horaria de la última semana y pronóstico de las próximas 24 h, para el modelo de IA.
     * Las fuentes que no lo ofrecen devuelven Optional.empty() y la zona queda sin predicción.
     * Nunca lanza excepción: la IA es un complemento y no debe frenar la sincronización.
     */
    default Optional<SerieLluvia> serieHoraria(String codigoEstacion) {
        return Optional.empty();
    }
}
