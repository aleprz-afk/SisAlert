package co.edu.unillanos.sisalert.alertas.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import co.edu.unillanos.sisalert.alertas.repository.AlertaRepository;
import co.edu.unillanos.sisalert.alertas.repository.LecturaRepository;

class RetencionDatosServiceTest {

    private final ZoneId bogota = ZoneId.of("America/Bogota");
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-20T08:00:00Z"), bogota);
    private final LecturaRepository lecturas = mock(LecturaRepository.class);
    private final AlertaRepository alertas = mock(AlertaRepository.class);

    @Test
    void borraLoQueSuperaLaRetencionConfigurada() {
        new RetencionDatosService(lecturas, alertas, 365, 730, reloj).limpiar();

        LocalDateTime ahora = LocalDateTime.now(reloj);
        verify(lecturas).borrarAnterioresA(ahora.minusDays(365));
        verify(alertas).borrarHistoricasAnterioresA(ahora.minusDays(730));
    }

    @Test
    void conCeroDiasNoBorraNada() {
        new RetencionDatosService(lecturas, alertas, 0, 0, reloj).limpiar();

        verify(lecturas, never()).borrarAnterioresA(any());
        verify(alertas, never()).borrarHistoricasAnterioresA(any());
    }
}
