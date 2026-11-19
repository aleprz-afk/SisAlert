package co.edu.unillanos.sisalert.alertas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.unillanos.sisalert.alertas.client.ClienteDatosExternos;
import co.edu.unillanos.sisalert.alertas.dto.AlertaDto;
import co.edu.unillanos.sisalert.alertas.exception.FuenteExternaException;
import co.edu.unillanos.sisalert.alertas.model.Alerta;
import co.edu.unillanos.sisalert.alertas.model.Lectura;
import co.edu.unillanos.sisalert.alertas.model.NivelAlerta;
import co.edu.unillanos.sisalert.alertas.model.Zona;
import co.edu.unillanos.sisalert.alertas.repository.AlertaRepository;
import co.edu.unillanos.sisalert.alertas.repository.LecturaRepository;
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

    @BeforeEach
    void setUp() {
        zonaRepository = mock(ZonaRepository.class);
        lecturaRepository = mock(LecturaRepository.class);
        alertaRepository = mock(AlertaRepository.class);
        cliente = mock(ClienteDatosExternos.class);
        regla = new ReglaDeUmbral(40, 80);
        alertaService = mock(AlertaService.class);

        service = new SincronizacionService(
                zonaRepository, lecturaRepository, alertaRepository, cliente, regla, alertaService);
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
                NivelAlerta.PRECAUCION, "Lluvia acumulada relevante", 55.0, null);
        when(alertaService.listarVigentes()).thenReturn(List.of(dtoEsperado));

        List<AlertaDto> resultado = service.sincronizar();

        assertEquals(1, resultado.size());
        assertEquals(NivelAlerta.PRECAUCION, resultado.get(0).nivel());
        verify(lecturaRepository).save(any(Lectura.class));
        verify(alertaRepository).save(any(Alerta.class));
    }
}
