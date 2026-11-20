package co.edu.unillanos.sisalert.alertas.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unillanos.sisalert.alertas.dto.ZonaDto;
import co.edu.unillanos.sisalert.alertas.dto.ZonaRequest;
import co.edu.unillanos.sisalert.alertas.exception.ConflictoException;
import co.edu.unillanos.sisalert.alertas.exception.RecursoNoEncontradoException;
import co.edu.unillanos.sisalert.alertas.model.Zona;
import co.edu.unillanos.sisalert.alertas.repository.ZonaRepository;

@Service
public class ZonaService {

    private final ZonaRepository zonaRepository;

    public ZonaService(ZonaRepository zonaRepository) {
        this.zonaRepository = zonaRepository;
    }

    @Transactional(readOnly = true)
    public List<ZonaDto> listar() {
        return zonaRepository.findAll(Sort.by("id")).stream()
                .map(this::aDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ZonaDto obtener(Long id) {
        return aDto(buscar(id));
    }

    @Transactional
    public ZonaDto crear(ZonaRequest request) {
        validarDuplicados(request, null);

        Zona zona = new Zona(
                request.nombre().trim(),
                request.cuerpoAgua().trim(),
                request.comunidades().trim(),
                request.codigoEstacion().trim());

        return aDto(zonaRepository.save(zona));
    }

    @Transactional
    public ZonaDto actualizar(Long id, ZonaRequest request) {
        Zona zona = buscar(id);
        validarDuplicados(request, id);

        zona.actualizar(
                request.nombre().trim(),
                request.cuerpoAgua().trim(),
                request.comunidades().trim(),
                request.codigoEstacion().trim());

        return aDto(zonaRepository.save(zona));
    }

    /** Las lecturas y alertas de la zona se eliminan en cascada (ver migración V2). */
    @Transactional
    public void eliminar(Long id) {
        Zona zona = buscar(id);
        zonaRepository.delete(zona);
    }

    private Zona buscar(Long id) {
        return zonaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la zona con id " + id));
    }

    private void validarDuplicados(ZonaRequest request, Long idActual) {
        String nombre = request.nombre().trim();
        String codigo = request.codigoEstacion().trim();

        boolean nombreDuplicado = idActual == null
                ? zonaRepository.existsByNombreIgnoreCase(nombre)
                : zonaRepository.existsByNombreIgnoreCaseAndIdNot(nombre, idActual);
        if (nombreDuplicado) {
            throw new ConflictoException("Ya existe una zona con el nombre '" + nombre + "'");
        }

        boolean codigoDuplicado = idActual == null
                ? zonaRepository.existsByCodigoEstacion(codigo)
                : zonaRepository.existsByCodigoEstacionAndIdNot(codigo, idActual);
        if (codigoDuplicado) {
            throw new ConflictoException("Ya existe una zona con la estación '" + codigo + "'");
        }
    }

    private ZonaDto aDto(Zona zona) {
        return new ZonaDto(
                zona.getId(),
                zona.getNombre(),
                zona.getCuerpoAgua(),
                zona.getComunidades(),
                zona.getCodigoEstacion());
    }
}
