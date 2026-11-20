package co.edu.unillanos.sisalert.alertas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.edu.unillanos.sisalert.alertas.dto.ZonaDto;
import co.edu.unillanos.sisalert.alertas.dto.ZonaRequest;
import co.edu.unillanos.sisalert.alertas.exception.ConflictoException;
import co.edu.unillanos.sisalert.alertas.exception.RecursoNoEncontradoException;
import co.edu.unillanos.sisalert.alertas.model.Zona;
import co.edu.unillanos.sisalert.alertas.repository.ZonaRepository;

class ZonaServiceTest {

    private ZonaRepository zonaRepository;
    private ZonaService service;

    @BeforeEach
    void setUp() {
        zonaRepository = mock(ZonaRepository.class);
        service = new ZonaService(zonaRepository);
    }

    private static ZonaRequest request(String nombre, String codigo) {
        return new ZonaRequest(nombre, "Río Guatiquía", "Barzal Alto", codigo);
    }

    @Test
    void listarIncluyeElCodigoDeEstacion() {
        Zona zona = new Zona("Zona Guatiquía Norte", "Río Guatiquía", "Barzal Alto", "0035035100");
        when(zonaRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of(zona));

        List<ZonaDto> zonas = service.listar();

        assertEquals(1, zonas.size());
        assertEquals("0035035100", zonas.get(0).codigoEstacion());
    }

    @Test
    void crearGuardaLaZonaCuandoNoHayDuplicados() {
        when(zonaRepository.existsByNombreIgnoreCase("Zona Nueva")).thenReturn(false);
        when(zonaRepository.existsByCodigoEstacion("999")).thenReturn(false);
        when(zonaRepository.save(any(Zona.class))).thenAnswer(inv -> inv.getArgument(0));

        ZonaDto creada = service.crear(request("Zona Nueva", "999"));

        assertEquals("Zona Nueva", creada.nombre());
        verify(zonaRepository).save(any(Zona.class));
    }

    @Test
    void crearRechazaNombreDuplicado() {
        when(zonaRepository.existsByNombreIgnoreCase("Zona Nueva")).thenReturn(true);

        assertThrows(ConflictoException.class, () -> service.crear(request("Zona Nueva", "999")));
        verify(zonaRepository, never()).save(any(Zona.class));
    }

    @Test
    void crearRechazaEstacionDuplicada() {
        when(zonaRepository.existsByCodigoEstacion("3502700090")).thenReturn(true);

        assertThrows(ConflictoException.class, () -> service.crear(request("Otra zona", "3502700090")));
        verify(zonaRepository, never()).save(any(Zona.class));
    }

    @Test
    void actualizarFallaSiLaZonaNoExiste() {
        when(zonaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.actualizar(99L, request("Zona", "123")));
    }

    @Test
    void eliminarBorraLaZonaEncontrada() {
        Zona zona = new Zona("Zona", "Río", "Comunidad", "123");
        when(zonaRepository.findById(1L)).thenReturn(Optional.of(zona));

        service.eliminar(1L);

        verify(zonaRepository).delete(zona);
    }
}
