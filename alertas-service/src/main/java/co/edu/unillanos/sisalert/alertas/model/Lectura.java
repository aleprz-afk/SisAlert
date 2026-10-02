package co.edu.unillanos.sisalert.alertas.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Lectura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Zona zona;

    @Column(nullable = false)
    private double precipitacionMm;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    @Column(nullable = false, length = 50)
    private String fuente;

    protected Lectura() { }

    public Lectura(Zona zona, double precipitacionMm, LocalDateTime fechaHora, String fuente) {
        this.zona = zona;
        this.precipitacionMm = precipitacionMm;
        this.fechaHora = fechaHora;
        this.fuente = fuente;
    }

    public Long getId() { return id; }
    public Zona getZona() { return zona; }
    public double getPrecipitacionMm() { return precipitacionMm; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public String getFuente() { return fuente; }
}