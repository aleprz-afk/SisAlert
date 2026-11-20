package co.edu.unillanos.sisalert.alertas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import co.edu.unillanos.sisalert.alertas.client.ClientePrediccionIa;
import co.edu.unillanos.sisalert.alertas.client.PrediccionIa;
import co.edu.unillanos.sisalert.alertas.dto.AlertaDto;
import co.edu.unillanos.sisalert.alertas.exception.RecursoNoEncontradoException;
import co.edu.unillanos.sisalert.alertas.model.NivelAlerta;
import co.edu.unillanos.sisalert.alertas.model.Zona;
import co.edu.unillanos.sisalert.alertas.repository.ZonaRepository;
import co.edu.unillanos.sisalert.alertas.rules.ReglaDeUmbral;

class SimulacionServiceTest {

    private ZonaRepository zonas;
    private ClientePrediccionIa ia;
    private SimulacionService service;

    @BeforeEach
    void setUp() {
        zonas = mock(ZonaRepository.class);
        ia = mock(ClientePrediccionIa.class);
        service = new SimulacionService(zonas, new ReglaDeUmbral(40, 80), ia, 40, 80);
        when(zonas.findAll(any(Sort.class))).thenReturn(List.of(
                new Zona("Zona Guatiquía Norte", "Río Guatiquía", "Barzal", "0035035100"),
                new Zona("Zona Guayuriba Sur", "Río Guayuriba", "Apiay", "3502700090"),
                new Zona("Zona Caño Grande Centro", "Caño Grande", "Porvenir", "0035025110")));
    }

    @Test
    void tormentaDaLosTresNivelesYLaPrediccionDeIaEnVivo() {
        when(ia.habilitado()).thenReturn(true);
        when(ia.predecir(any())).thenReturn(Optional.of(new PrediccionIa(0.3, "ALTO", List.of(), "2.0.0")));

        List<AlertaDto> resultado = service.simular("Tormenta");

        assertEquals(List.of(NivelAlerta.PRECAUCION, NivelAlerta.PELIGRO, NivelAlerta.NORMAL),
                resultado.stream().map(AlertaDto::nivel).toList());
        assertEquals("ALTO", resultado.get(1).riesgoIa().riesgo());
    }

    @Test
    void sinIaLaSimulacionFuncionaIgualConElSemaforo() {
        when(ia.habilitado()).thenReturn(false);

        List<AlertaDto> resultado = service.simular("creciente");

        resultado.forEach(z -> assertEquals(NivelAlerta.PELIGRO, z.nivel()));
        resultado.forEach(z -> assertNull(z.riesgoIa()));
    }

    @Test
    void escenarioInexistenteResponde404() {
        assertThrows(RecursoNoEncontradoException.class, () -> service.simular("huracan"));
        verifyNoInteractions(ia);
    }
}
