package co.edu.unillanos.sisalert.alertas.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Zona zona;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NivelAlerta nivel;

    @Column(nullable = false)
    private String mensaje;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    @Column(nullable = false)
    private boolean vigente;

    protected Alerta() { }

    public Alerta(Zona zona, NivelAlerta nivel, String mensaje, LocalDateTime fechaHora) {
        this.zona = zona;
        this.nivel = nivel;
        this.mensaje = mensaje;
        this.fechaHora = fechaHora;
        this.vigente = true;
    }

    public Long getId() { return id; }
    public Zona getZona() { return zona; }
    public NivelAlerta getNivel() { return nivel; }
    public String getMensaje() { return mensaje; }
    public LocalDateTime getFechaHora() { return fechaHora; }
    public boolean isVigente() { return vigente; }
    public void setVigente(boolean vigente) { this.vigente = vigente; }
}