package co.edu.unillanos.sisalert.alertas.client;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Fuente simulada: permite probar y demostrar mientras no hay conexión real al IDEAM. */
@Component
@ConditionalOnProperty(name = "alertas.fuente", havingValue = "simulada", matchIfMissing = true)
public class ClienteSimulado implements ClienteDatosExternos {

    @Override
    public String nombreFuente() {
        return "SIMULADA";
    }

    @Override
    public double precipitacionAcumuladaMm(String codigoEstacion) {
        double mm = ThreadLocalRandom.current().nextDouble(0, 90);
        return Math.round(mm * 10.0) / 10.0;
    }
}