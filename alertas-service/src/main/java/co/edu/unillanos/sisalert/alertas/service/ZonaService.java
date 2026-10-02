package co.edu.unillanos.sisalert.alertas.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import co.edu.unillanos.sisalert.alertas.dto.ZonaDto;
import co.edu.unillanos.sisalert.alertas.repository.ZonaRepository;

@Service
public class ZonaService {

    private final ZonaRepository zonaRepository;

    public ZonaService(ZonaRepository zonaRepository) {
        this.zonaRepository = zonaRepository;
    }

    public List<ZonaDto> listar() {
        return zonaRepository.findAll(Sort.by("id")).stream()
                .map(z -> new ZonaDto(z.getId(), z.getNombre(), z.getCuerpoAgua(), z.getComunidades()))
                .toList();
    }
}