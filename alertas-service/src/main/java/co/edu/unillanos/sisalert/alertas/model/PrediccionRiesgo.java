package co.edu.unillanos.sisalert.alertas.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Predicción del servicio de IA para una zona en una sincronización (tabla prediccion_ia). */
@Entity
@Table(name = "prediccion_ia")
public class PrediccionRiesgo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Zona zona;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    /** P(lluvia >= 40 mm en las próximas 24 h), entre 0 y 1. */
    @Column(nullable = false)
    private double probabilidad;

    /** BAJO | MEDIO | ALTO */
    @Column(nullable = false, length = 10)
    private String riesgo;

    /** Factores que más pesaron, en JSON: [{"descripcion","efecto","aporte"}]. */
    @Column(nullable = false, length = 1000)
    private String factores;

    @Column(nullable = false, length = 20)
    private String versionModelo;

    protected PrediccionRiesgo() { }

    public PrediccionRiesgo(Zona zona, LocalDateTime fechaHora, double probabilidad, String riesgo,
                            String factores, String versionModelo) {
        this.zona = zona;
        this.fechaHora = fechaHora;
        this.probabilidad = probabilidad;
        this.riesgo = riesgo;
        this.factores = factores;
        this.versionModelo = versionModelo;
    }

    public Long getId() { return id; }
    public Zona getZona() { return zona; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public double getProbabilidad() { return probabilidad; }
    public String getRiesgo() { return riesgo; }
    public String getFactores() { return factores; }
    public String getVersionModelo() { return versionModelo; }
}
