package co.edu.unillanos.sisalert.alertas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class SerieHorariaTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Respuesta de Open-Meteo con 9 días de horas (past_days=7, forecast_days=2). */
    private ObjectNode respuesta(LocalDateTime inicio, int horas, Double valorFaltante, int posicionFaltante) {
        ObjectNode raiz = mapper.createObjectNode();
        ArrayNode tiempos = raiz.putObject("hourly").putArray("time");
        ArrayNode lluvia = ((ObjectNode) raiz.get("hourly")).putArray("precipitation");
        for (int i = 0; i < horas; i++) {
            tiempos.add(inicio.plusHours(i).toString());
            if (i == posicionFaltante && valorFaltante == null) {
                lluvia.addNull();
            } else {
                lluvia.add(i % 10 == 0 ? 2.0 : 0.0);
            }
        }
        return raiz;
    }

    @Test
    void openMeteoTomaLaSemanaPasadaYLas24HorasSiguientes() {
        LocalDateTime inicio = LocalDateTime.of(2026, 11, 13, 0, 0);
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 20, 10, 0); // hora 178 de la respuesta
        Optional<SerieLluvia> serie = ClienteOpenMeteo.armarSerie(respuesta(inicio, 216, 0.0, -1), ahora);

        assertTrue(serie.isPresent());
        assertEquals(SerieLluvia.HORAS_HISTORIA, serie.get().pasadasMm().size());
        assertEquals(SerieLluvia.HORAS_PRONOSTICO, serie.get().pronosticoMm().size());
        assertEquals(ahora, serie.get().horaActual());
        // La serie empieza en la hora 178 - 167 = 11 de la respuesta: la hora 170 (con lluvia)
        // queda en la posición 159 y la hora actual (178, sin lluvia) en la última.
        assertEquals(2.0, serie.get().pasadasMm().get(159));
        assertEquals(0.0, serie.get().pasadasMm().get(SerieLluvia.HORAS_HISTORIA - 1));
        assertEquals(2.0, serie.get().pronosticoMm().get(1)); // hora 180
    }

    @Test
    void openMeteoSinHistoriaSuficienteOConValoresVaciosNoDaSerie() {
        LocalDateTime inicio = LocalDateTime.of(2026, 11, 13, 0, 0);
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 20, 10, 0);
        assertTrue(ClienteOpenMeteo.armarSerie(respuesta(inicio, 216, null, 100), ahora).isEmpty());
        assertTrue(ClienteOpenMeteo.armarSerie(respuesta(inicio, 216, 0.0, -1), inicio.plusHours(5)).isEmpty());
    }

    @Test
    void escenarioDeTormentaDaUnaSerieMasLluviosaParaLaZonaEnPeligro() {
        ClienteEscenario escenario = new ClienteEscenario("tormenta", 40, 80);
        SerieLluvia peligro = escenario.serieHoraria(ClienteEscenario.GUAYURIBA_SUR).orElseThrow();
        SerieLluvia normal = escenario.serieHoraria("0035025110").orElseThrow();

        double ultimoDiaPeligro = peligro.pasadasMm().subList(144, 168).stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(92.0, ultimoDiaPeligro, 0.5);
        assertTrue(peligro.pronosticoMm().get(0) > normal.pronosticoMm().get(0));
    }
}
