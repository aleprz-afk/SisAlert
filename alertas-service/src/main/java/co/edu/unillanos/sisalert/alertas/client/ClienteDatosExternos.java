package co.edu.unillanos.sisalert.alertas.client;

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
}
