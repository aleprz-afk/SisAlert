package co.edu.unillanos.sisalert.alertas.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unillanos.sisalert.alertas.model.BoletinZona;

public interface BoletinRepository extends JpaRepository<BoletinZona, Long> {

    Optional<BoletinZona> findFirstByZonaIdOrderByFechaHoraDescIdDesc(Long zonaId);

    /** Retención: borra los boletines anteriores a la fecha límite. */
    @Modifying
    @Query("delete from BoletinZona b where b.fechaHora < :limite")
    int borrarAnterioresA(@Param("limite") LocalDateTime limite);
}
