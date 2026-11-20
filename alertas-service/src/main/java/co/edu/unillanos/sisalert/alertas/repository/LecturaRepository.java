package co.edu.unillanos.sisalert.alertas.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unillanos.sisalert.alertas.model.Lectura;

public interface LecturaRepository extends JpaRepository<Lectura, Long> {
    Optional<Lectura> findFirstByZonaIdOrderByFechaHoraDescIdDesc(Long zonaId);
    List<Lectura> findTop20ByZonaIdOrderByFechaHoraDesc(Long zonaId);

    /** Borra en una sola sentencia las lecturas anteriores a la fecha límite (retención). */
    @Modifying
    @Query("delete from Lectura l where l.fechaHora < :limite")
    int borrarAnterioresA(@Param("limite") LocalDateTime limite);
}