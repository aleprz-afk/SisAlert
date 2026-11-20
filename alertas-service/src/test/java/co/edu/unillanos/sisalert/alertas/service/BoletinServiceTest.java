package co.edu.unillanos.sisalert.alertas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.unillanos.sisalert.alertas.client.BoletinIa;
import co.edu.unillanos.sisalert.alertas.client.ClientePrediccionIa;
import co.edu.unillanos.sisalert.alertas.client.PrediccionIa;
import co.edu.unillanos.sisalert.alertas.model.BoletinZona;
import co.edu.unillanos.sisalert.alertas.model.NivelAlerta;
import co.edu.unillanos.sisalert.alertas.model.Zona;
import co.edu.unillanos.sisalert.alertas.repository.BoletinRepository;

class BoletinServiceTest {

    private final LocalDateTime ahora = LocalDateTime.of(2026, 11, 20, 10, 0);
    private final Zona zona = new Zona("Zona Guayuriba Sur", "Río Guayuriba", "Apiay, Catumare", "3502700090");
    private final PrediccionIa alto = new PrediccionIa(0.24, "ALTO",
            List.of(new PrediccionIa.Factor("Lluvia pronosticada para las próximas 24 horas", "aumenta", 1.9)), "2.0.0");

    private BoletinRepository repo;
    private ClientePrediccionIa ia;
    private BoletinService service;

    @BeforeEach
    void setUp() {
        repo = mock(BoletinRepository.class);
        ia = mock(ClientePrediccionIa.class);
        when(ia.habilitado()).thenReturn(true);
        when(ia.redactarBoletin(any())).thenReturn(Optional.of(new BoletinIa("Vecinos de Apiay...", "gemini", "m")));
        service = new BoletinService(repo, ia);
    }

    private void ultimo(String clave, String fuente, LocalDateTime cuando) {
        when(repo.findFirstByZonaIdOrderByFechaHoraDescIdDesc(any()))
                .thenReturn(Optional.of(new BoletinZona(zona, clave, "texto", fuente, cuando)));
    }

    @Test
    void siCambiaElEstadoPideUnBoletinNuevo() {
        ultimo("PRECAUCION|BAJO", "gemini", ahora.minusHours(2));
        service.actualizar(zona, NivelAlerta.PELIGRO, 92, alto, ahora);
        verify(ia).redactarBoletin(any());
        verify(repo).save(any(BoletinZona.class));
    }

    @Test
    void siElEstadoNoCambiaNoLlamaAGemini() {
        ultimo("PELIGRO|ALTO", "gemini", ahora.minusHours(5));
        service.actualizar(zona, NivelAlerta.PELIGRO, 95, alto, ahora);
        verify(ia, never()).redactarBoletin(any());
    }

    @Test
    void reintentaSiElUltimoFuePlantillaYPasoMediaHora() {
        ultimo("PELIGRO|ALTO", "plantilla", ahora.minusMinutes(31));
        service.actualizar(zona, NivelAlerta.PELIGRO, 95, alto, ahora);
        verify(ia).redactarBoletin(any());
    }

    @Test
    void zonaNormalNoTieneBoletin() {
        service.actualizar(zona, NivelAlerta.NORMAL, 2, null, ahora);
        verify(ia, never()).redactarBoletin(any());
        assertNull(service.vigente(zona, NivelAlerta.NORMAL));
    }

    @Test
    void elBoletinVigenteDebeCorresponderAlNivelActual() {
        ultimo("PELIGRO|ALTO", "gemini", ahora);
        assertNull(service.vigente(zona, NivelAlerta.PRECAUCION));
        assertEquals("texto", service.vigente(zona, NivelAlerta.PELIGRO).texto());
    }

    @Test
    void laSimulacionLlamaUnaSolaVezPorZonaYEstado() {
        for (int i = 0; i < 5; i++) {
            service.simulado(zona, NivelAlerta.PELIGRO, 92, alto, "tormenta");
        }
        verify(ia, times(1)).redactarBoletin(any());
        verify(repo, never()).save(any());
    }

    @Test
    void enLaSimulacionLaPlantillaTambienSeReutilizaUnosMinutos() {
        when(ia.redactarBoletin(any())).thenReturn(Optional.of(new BoletinIa("Atención...", "plantilla", null)));
        for (int i = 0; i < 5; i++) {
            service.simulado(zona, NivelAlerta.PELIGRO, 92, alto, "tormenta");
        }
        verify(ia, times(1)).redactarBoletin(any());
    }

    @Test
    void geminiSoloRecibeDatosEstructurados() {
        Map<String, Object> datos = BoletinService.datosParaBoletin(zona, NivelAlerta.PELIGRO, 92, alto);
        assertEquals(List.of("zona", "cuerpoAgua", "comunidades", "nivel", "precipitacionMm", "riesgoIa"),
                List.copyOf(datos.keySet()));
    }
}
