package co.edu.unillanos.sisalert.alertas.exception;

/** Operación válida en forma pero que entra en conflicto con los datos actuales (se responde 409). */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
