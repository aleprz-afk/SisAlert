package co.edu.unillanos.sisalert.alertas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Zona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String cuerpoAgua;

    @Column(nullable = false)
    private String comunidades;

    @Column(nullable = false, length = 50)
    private String codigoEstacion;

    protected Zona() { }

    public Zona(String nombre, String cuerpoAgua, String comunidades, String codigoEstacion) {
        this.nombre = nombre;
        this.cuerpoAgua = cuerpoAgua;
        this.comunidades = comunidades;
        this.codigoEstacion = codigoEstacion;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getCuerpoAgua() { return cuerpoAgua; }
    public String getComunidades() { return comunidades; }
    public String getCodigoEstacion() { return codigoEstacion; }
}