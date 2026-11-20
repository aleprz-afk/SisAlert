package co.edu.unillanos.sisalert.alertas.dto;

import java.time.LocalDateTime;

import co.edu.unillanos.sisalert.alertas.model.NivelAlerta;

/**
 * Una entrada por zona. Si la zona no tiene alerta, nivel/mensaje/precipitacionMm/fechaHora van en null.
 * riesgoIa es la última predicción del servicio de IA (null si la IA no está disponible).
 * boletin es el mensaje para la comunidad (solo zonas en PRECAUCION o PELIGRO).
 */
public record AlertaDto(
        Long zonaId,
        String zona,
        String cuerpoAgua,
        String comunidades,
        NivelAlerta nivel,
        String mensaje,
        Double precipitacionMm,
        LocalDateTime fechaHora,
        RiesgoIaDto riesgoIa,
        BoletinDto boletin) {
}