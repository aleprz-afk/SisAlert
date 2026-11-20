package co.edu.unillanos.sisalert.alertas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos que envía el frontend para crear o editar una zona de riesgo. */
public record ZonaRequest(
        @NotBlank(message = "El nombre de la zona es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String nombre,

        @NotBlank(message = "El cuerpo de agua es obligatorio")
        @Size(max = 255, message = "El cuerpo de agua no puede superar 255 caracteres")
        String cuerpoAgua,

        @NotBlank(message = "Las comunidades son obligatorias")
        @Size(max = 255, message = "Las comunidades no pueden superar 255 caracteres")
        String comunidades,

        @NotBlank(message = "El código de estación es obligatorio")
        @Size(max = 50, message = "El código de estación no puede superar 50 caracteres")
        String codigoEstacion) {
}
