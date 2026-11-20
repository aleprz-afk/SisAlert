package co.edu.unillanos.sisalert.alertas.exception;

/** Recurso solicitado que no existe (se responde 404). */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
