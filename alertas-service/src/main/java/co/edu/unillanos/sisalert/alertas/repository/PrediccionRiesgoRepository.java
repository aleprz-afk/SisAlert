package co.edu.unillanos.sisalert.alertas.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import co.edu.unillanos.sisalert.alertas.model.PrediccionRiesgo;

public interface PrediccionRiesgoRepository extends JpaRepository<PrediccionRiesgo, Long> {

    Optional<PrediccionRiesgo> findFirstByZonaIdOrderByFechaHoraDescIdDesc(Long zonaId);

    /** Retención: borra las predicciones anteriores a la fecha límite. */
    @Modifying
    @Query("delete from PrediccionRiesgo p where p.fechaHora < :limite")
    int borrarAnterioresA(@Param("limite") LocalDateTime limite);
}
