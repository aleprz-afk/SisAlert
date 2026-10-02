package co.edu.unillanos.sisalert.alertas.rules;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import co.edu.unillanos.sisalert.alertas.model.NivelAlerta;

/** Convierte la lluvia acumulada del día (mm) en un nivel de alerta. */
@Component
public class ReglaDeUmbral {

    private final double umbralPrecaucionMm;
    private final double umbralPeligroMm;

    public ReglaDeUmbral(
            @Value("${alertas.umbral.precaucion-mm}") double umbralPrecaucionMm,
            @Value("${alertas.umbral.peligro-mm}") double umbralPeligroMm) {
        if (umbralPrecaucionMm < 0 || umbralPrecaucionMm >= umbralPeligroMm) {
            throw new IllegalArgumentException(
                    "Umbrales inválidos: precaución debe ser >= 0 y menor que peligro");
        }
        this.umbralPrecaucionMm = umbralPrecaucionMm;
        this.umbralPeligroMm = umbralPeligroMm;
    }

    public NivelAlerta evaluar(double precipitacionMm) {
        if (precipitacionMm >= umbralPeligroMm) {
            return NivelAlerta.PELIGRO;
        }
        if (precipitacionMm >= umbralPrecaucionMm) {
            return NivelAlerta.PRECAUCION;
        }
        return NivelAlerta.NORMAL;
    }

    public String mensajePara(NivelAlerta nivel) {
        return switch (nivel) {
            case NORMAL -> "Sin riesgo por lluvias en este momento.";
            case PRECAUCION -> "Lluvia acumulada relevante. Manténgase atento a la evolución del río.";
            case PELIGRO -> "Lluvia intensa. Prepárese para una posible evacuación.";
        };
    }
}
