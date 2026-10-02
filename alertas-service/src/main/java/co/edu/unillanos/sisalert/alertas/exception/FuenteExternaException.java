package co.edu.unillanos.sisalert.alertas.exception;

/** Se lanza cuando la fuente de datos meteorológicos falla o devuelve datos inválidos. */
public class FuenteExternaException extends RuntimeException {

    public FuenteExternaException(String mensaje) {
        super(mensaje);
    }

    public FuenteExternaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}