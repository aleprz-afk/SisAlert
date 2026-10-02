package co.edu.unillanos.sisalert.alertas.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import co.edu.unillanos.sisalert.alertas.model.NivelAlerta;

class ReglaDeUmbralTest {

    private final ReglaDeUmbral regla = new ReglaDeUmbral(30, 60);

    @Test
    void menorAlUmbralDePrecaucionEsNormal() {
        assertEquals(NivelAlerta.NORMAL, regla.evaluar(0));
        assertEquals(NivelAlerta.NORMAL, regla.evaluar(29.9));
    }

    @Test
    void desdeElUmbralDePrecaucionHastaAntesDelPeligroEsPrecaucion() {
        assertEquals(NivelAlerta.PRECAUCION, regla.evaluar(30));
        assertEquals(NivelAlerta.PRECAUCION, regla.evaluar(59.9));
    }

    @Test
    void desdeElUmbralDePeligroEsPeligro() {
        assertEquals(NivelAlerta.PELIGRO, regla.evaluar(60));
        assertEquals(NivelAlerta.PELIGRO, regla.evaluar(120));
    }

    @Test
    void umbralesInvalidosSeRechazan() {
        assertThrows(IllegalArgumentException.class, () -> new ReglaDeUmbral(60, 30));
    }
}
