-- V4: boletines para la comunidad (redactados con Gemini o con la plantilla de respaldo).
-- Se guarda uno nuevo solo cuando cambia el estado de la zona (nivel y riesgo de la IA).

CREATE TABLE boletin (
    id           BIGSERIAL PRIMARY KEY,
    zona_id      BIGINT        NOT NULL REFERENCES zona (id) ON DELETE CASCADE,
    clave_estado VARCHAR(30)   NOT NULL,
    texto        VARCHAR(1000) NOT NULL,
    fuente       VARCHAR(20)   NOT NULL CHECK (fuente IN ('gemini', 'plantilla')),
    fecha_hora   TIMESTAMP     NOT NULL
);

CREATE INDEX ix_boletin_zona_fecha ON boletin (zona_id, fecha_hora DESC);
