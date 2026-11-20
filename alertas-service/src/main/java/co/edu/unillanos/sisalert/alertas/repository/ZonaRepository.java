package co.edu.unillanos.sisalert.alertas.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import co.edu.unillanos.sisalert.alertas.model.Zona;

public interface ZonaRepository extends JpaRepository<Zona, Long> {

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

    boolean existsByCodigoEstacion(String codigoEstacion);

    boolean existsByCodigoEstacionAndIdNot(String codigoEstacion, Long id);
}
