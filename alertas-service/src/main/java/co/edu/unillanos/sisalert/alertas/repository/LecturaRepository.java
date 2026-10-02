package co.edu.unillanos.sisalert.alertas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unillanos.sisalert.alertas.model.Lectura;

public interface LecturaRepository extends JpaRepository<Lectura, Long> {
    Optional<Lectura> findFirstByZonaIdOrderByFechaHoraDescIdDesc(Long zonaId);
}