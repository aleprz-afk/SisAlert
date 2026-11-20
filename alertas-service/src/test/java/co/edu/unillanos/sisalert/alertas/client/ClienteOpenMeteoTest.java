package co.edu.unillanos.sisalert.alertas.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unillanos.sisalert.alertas.exception.FuenteExternaException;

class ClienteOpenMeteoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String texto) throws Exception {
        return mapper.readTree(texto);
    }

    @Test
    void leeLaLluviaDeHoyRedondeada() throws Exception {
        double mm = ClienteOpenMeteo.leerPrecipitacionHoy(
                json("{\"daily\":{\"precipitation_sum\":[12.34,5.0]}}"), "X");
        assertEquals(12.3, mm);
    }

    @Test
    void ceroEsUnDatoValido() throws Exception {
        assertEquals(0.0, ClienteOpenMeteo.leerPrecipitacionHoy(
                json("{\"daily\":{\"precipitation_sum\":[0]}}"), "X"));
    }

    @Test
    void valorNuloEsFallaDeLaFuenteYNoCeroMm() throws Exception {
        JsonNode respuesta = json("{\"daily\":{\"precipitation_sum\":[null]}}");
        assertThrows(FuenteExternaException.class,
                () -> ClienteOpenMeteo.leerPrecipitacionHoy(respuesta, "X"));
    }

    @Test
    void respuestaSinDatosEsFallaDeLaFuente() throws Exception {
        JsonNode respuesta = json("{\"daily\":{}}");
        assertThrows(FuenteExternaException.class,
                () -> ClienteOpenMeteo.leerPrecipitacionHoy(respuesta, "X"));
    }
}
