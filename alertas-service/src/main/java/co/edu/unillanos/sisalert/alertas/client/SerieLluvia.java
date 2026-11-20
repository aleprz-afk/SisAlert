package co.edu.unillanos.sisalert.alertas.client;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que necesita el modelo de IA para una zona: la lluvia horaria de la última semana
 * y el pronóstico horario de las próximas 24 horas.
 *
 * @param pasadasMm   lluvia de las últimas {@value #HORAS_HISTORIA} horas, la más antigua primero
 * @param pronosticoMm lluvia pronosticada para las próximas {@value #HORAS_PRONOSTICO} horas
 * @param horaActual  hora local (America/Bogota) de la última hora de pasadasMm
 */
public record SerieLluvia(List<Double> pasadasMm, List<Double> pronosticoMm, LocalDateTime horaActual) {

    public static final int HORAS_HISTORIA = 168;
    public static final int HORAS_PRONOSTICO = 24;

    public SerieLluvia {
        if (pasadasMm.size() != HORAS_HISTORIA || pronosticoMm.size() != HORAS_PRONOSTICO) {
            throw new IllegalArgumentException("La serie debe tener " + HORAS_HISTORIA
                    + " horas pasadas y " + HORAS_PRONOSTICO + " de pronóstico");
        }
        pasadasMm = List.copyOf(pasadasMm);
        pronosticoMm = List.copyOf(pronosticoMm);
    }
}
