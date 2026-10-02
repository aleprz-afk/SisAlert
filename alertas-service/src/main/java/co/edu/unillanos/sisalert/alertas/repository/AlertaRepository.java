package co.edu.unillanos.sisalert.alertas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unillanos.sisalert.alertas.model.Alerta;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {
    List<Alerta> findByZonaIdAndVigenteTrue(Long zonaId);
}