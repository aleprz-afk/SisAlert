package co.edu.unillanos.sisalert.alertas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import co.edu.unillanos.sisalert.alertas.client.ClienteDatosExternos;
import co.edu.unillanos.sisalert.alertas.client.ClientePrediccionIa;
import co.edu.unillanos.sisalert.alertas.client.PrediccionIa;
import co.edu.unillanos.sisalert.alertas.client.SerieLluvia;
import co.edu.unillanos.sisalert.alertas.dto.AlertaDto;
import co.edu.unillanos.sisalert.alertas.exception.FuenteExternaException;
import co.edu.unillanos.sisalert.alertas.model.Alerta;
import co.edu.unillanos.sisalert.alertas.model.Lectura;
import co.edu.unillanos.sisalert.alertas.model.NivelAlerta;
import co.edu.unillanos.sisalert.alertas.model.PrediccionRiesgo;
import co.edu.unillanos.sisalert.alertas.model.Zona;
import co.edu.unillanos.sisalert.alertas.repository.AlertaRepository;
import co.edu.unillanos.sisalert.alertas.repository.LecturaRepository;
import co.edu.unillanos.sisalert.alertas.repository.PrediccionRiesgoRepository;
import co.edu.unillanos.sisalert.alertas.repository.ZonaRepository;
import co.edu.unillanos.sisalert.alertas.rules.ReglaDeUmbral;

class SincronizacionServiceTest {

    private ZonaRepository zonaRepository;
    private LecturaRepository lecturaRepository;
    private AlertaRepository alertaRepository;
    private ClienteDatosExternos cliente;
    private ReglaDeUmbral regla;
    private AlertaService alertaService;
    private SincronizacionService service;
    private ClientePrediccionIa clienteIa;
    private PrediccionRiesgoRepository prediccionRepository;

    @BeforeEach
    void setUp() {
        zonaRepository = mock(ZonaRepository.class);
        lecturaRepository = mock(LecturaRepository.class);
        alertaRepository = mock(AlertaRepository.class);
        cliente = mock(ClienteDatosExternos.class);
        regla = new ReglaDeUmbral(40, 80);
        alertaService = mock(AlertaService.class);

        clienteIa = mock(ClientePrediccionIa.class);
        prediccionRepository = mock(PrediccionRiesgoRepository.class);

        service = new SincronizacionService(zonaRepository, lecturaRepository, alertaRepository,
                cliente, regla, alertaService, clienteIa, prediccionRepository);
    }

    private static SerieLluvia serieSeca() {
        return new SerieLluvia(Collections.nCopies(SerieLluvia.HORAS_HISTORIA, 0.0),
                Collections.nCopies(SerieLluvia.HORAS_PRONOSTICO, 0.0), LocalDateTime.of(2026, 11, 20, 10, 0));
    }

    private Zona zonaConLluvia(double mm) {
        Zona zona = new Zona("Zona 1", "Río Guatiquía", "Barzal", "0035035100");
        when(zonaRepository.findAll()).thenReturn(List.of(zona));
        when(cliente.precipitacionAcumuladaMm("0035035100")).thenReturn(mm);
        when(cliente.nombreFuente()).thenReturn("PRUEBA");
        return zona;
    }

    @Test
    void conIaDisponibleGuardaLaPrediccionJuntoConLaAlerta() {
        zonaConLluvia(10.0);
        when(clienteIa.habilitado()).thenReturn(true);
        when(cliente.serieHoraria("0035035100")).thenReturn(Optional.of(serieSeca()));
        when(clienteIa.predecir(any())).thenReturn(Optional.of(new PrediccionIa(0.72, "ALTO",
                List.of(new PrediccionIa.Factor("Lluvia pronosticada para las próximas 24 horas", "aumenta", 2.1)),
                "2.0.0")));

        service.sincronizar();

        ArgumentCaptor<PrediccionRiesgo> guardada = ArgumentCaptor.forClass(PrediccionRiesgo.class);
        verify(prediccionRepository).save(guardada.capture());
        assertEquals("ALTO", guardada.getValue().getRiesgo());
        assertEquals(0.72, guardada.getValue().getProbabilidad());
        assertTrue(guardada.getValue().getFactores().contains("pronosticada"));
        verify(alertaRepository).save(any(Alerta.class));
    }

    @Test
    void siLaIaFallaLaSincronizacionSigueSinPrediccion() {
        zonaConLluvia(90.0);
        when(clienteIa.habilitado()).thenReturn(true);
        when(cliente.serieHoraria("0035035100")).thenReturn(Optional.of(serieSeca()));
        when(clienteIa.predecir(any())).thenReturn(Optional.empty());

        service.sincronizar();

        verify(alertaRepository).save(any(Alerta.class));
        verify(lecturaRepository).save(any(Lectura.class));
        verify(prediccionRepository, never()).save(any());
    }

    @Test
    void conIaDesactivadaNiSiquieraPideLaSerieHoraria() {
        zonaConLluvia(10.0);
        when(clienteIa.habilitado()).thenReturn(false);

        service.sincronizar();

        verify(cliente, never()).serieHoraria(any());
        verify(prediccionRepository, never()).save(any());
    }

    @Test
    void siFuenteFallaLanzaExcepcionYNoPersisteNada() {
        Zona zona = new Zona("Zona 1", "Río Guatiquía", "Barzal", "0035035100");
        when(zonaRepository.findAll()).thenReturn(List.of(zona));
        when(cliente.precipitacionAcumuladaMm("0035035100"))
                .thenThrow(new FuenteExternaException("Fallo en la conexión"));

        assertThrows(FuenteExternaException.class, () -> service.sincronizar());

        verify(lecturaRepository, never()).save(any(Lectura.class));
        verify(alertaRepository, never()).save(any(Alerta.class));
    }

    @Test
    void siSincronizacionEsExitosaPersisteLecturasYAlertas() {
        Zona zona = new Zona("Zona 1", "Río Guatiquía", "Barzal", "0035035100");
        when(zonaRepository.findAll()).thenReturn(List.of(zona));
        when(cliente.precipitacionAcumuladaMm("0035035100")).thenReturn(55.0);
        when(cliente.nombreFuente()).thenReturn("IDEAM");

        AlertaDto dtoEsperado = new AlertaDto(
                1L, "Zona 1", "Río Guatiquía", "Barzal",
                NivelAlerta.PRECAUCION, "Lluvia acumulada relevante", 55.0, null, null);
        when(alertaService.listarVigentes()).thenReturn(List.of(dtoEsperado));

        List<AlertaDto> resultado = service.sincronizar();

        assertEquals(1, resultado.size());
        assertEquals(NivelAlerta.PRECAUCION, resultado.get(0).nivel());
        verify(lecturaRepository).save(any(Lectura.class));
        verify(alertaRepository).save(any(Alerta.class));
    }
}
