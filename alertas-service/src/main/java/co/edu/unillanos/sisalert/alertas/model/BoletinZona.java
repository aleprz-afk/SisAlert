package co.edu.unillanos.sisalert.alertas.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Boletín para la comunidad de una zona, redactado por Gemini o con la plantilla (tabla boletin). */
@Entity
@Table(name = "boletin")
public class BoletinZona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Zona zona;

    /** Estado para el que se redactó: "NIVEL|RIESGO_IA", p. ej. "PELIGRO|ALTO". */
    @Column(nullable = false, length = 30)
    private String claveEstado;

    @Column(nullable = false, length = 1000)
    private String texto;

    /** "gemini" o "plantilla". */
    @Column(nullable = false, length = 20)
    private String fuente;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    protected BoletinZona() { }

    public BoletinZona(Zona zona, String claveEstado, String texto, String fuente, LocalDateTime fechaHora) {
        this.zona = zona;
        this.claveEstado = claveEstado;
        this.texto = texto.length() > 1000 ? texto.substring(0, 1000) : texto;
        this.fuente = fuente;
        this.fechaHora = fechaHora;
    }

    public Long getId() { return id; }
    public Zona getZona() { return zona; }
    public String getClaveEstado() { return claveEstado; }
    public String getTexto() { return texto; }
    public String getFuente() { return fuente; }
    public LocalDateTime getFechaHora() { return fechaHora; }
}
