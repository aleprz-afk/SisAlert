package co.edu.unillanos.sisalert.alertas.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unillanos.sisalert.alertas.model.Alerta;

public interface AlertaRepository extends JpaRepository<Alerta, Long> {
    List<Alerta> findByZonaIdAndVigenteTrue(Long zonaId);

    /** Borra alertas históricas (no vigentes) anteriores a la fecha límite. Nunca toca la vigente. */
    @Modifying
    @Query("delete from Alerta a where a.vigente = false and a.fechaHora < :limite")
    int borrarHistoricasAnterioresA(@Param("limite") LocalDateTime limite);
}