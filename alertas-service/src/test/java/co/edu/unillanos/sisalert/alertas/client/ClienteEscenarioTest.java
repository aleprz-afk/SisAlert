package co.edu.unillanos.sisalert.alertas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import co.edu.unillanos.sisalert.alertas.model.NivelAlerta;
import co.edu.unillanos.sisalert.alertas.rules.ReglaDeUmbral;

class ClienteEscenarioTest {

    private static final String CANO_GRANDE = "0035025110";
    private final ReglaDeUmbral regla = new ReglaDeUmbral(40, 80);

    private NivelAlerta nivel(String escenario, String estacion) {
        ClienteEscenario cliente = new ClienteEscenario(escenario, 40, 80);
        return regla.evaluar(cliente.precipitacionAcumuladaMm(estacion));
    }

    @Test
    void tormentaDaUnaZonaPorNivel() {
        assertEquals(NivelAlerta.PRECAUCION, nivel("tormenta", ClienteEscenario.GUATIQUIA_NORTE));
        assertEquals(NivelAlerta.PELIGRO, nivel("tormenta", ClienteEscenario.GUAYURIBA_SUR));
        assertEquals(NivelAlerta.NORMAL, nivel("tormenta", CANO_GRANDE));
    }

    @Test
    void secaYCrecienteAfectanATodasLasZonas() {
        assertEquals(NivelAlerta.NORMAL, nivel("seca", ClienteEscenario.GUAYURIBA_SUR));
        assertEquals(NivelAlerta.PELIGRO, nivel("creciente", CANO_GRANDE));
        assertEquals(NivelAlerta.PELIGRO, nivel("creciente", "codigo-de-zona-nueva"));
    }

    @Test
    void escenarioDesconocidoFallaAlArrancar() {
        assertThrows(IllegalArgumentException.class, () -> new ClienteEscenario("lluvioso", 40, 80));
    }
}
