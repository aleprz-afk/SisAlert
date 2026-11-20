-- V3: predicciones del servicio de IA (riesgo de lluvia fuerte en las próximas 24 h).
-- Una fila por zona en cada sincronización en la que la IA respondió. Si la IA no está
-- disponible no se guarda nada y la regla de umbral sigue funcionando igual.

CREATE TABLE prediccion_ia (
    id             BIGSERIAL PRIMARY KEY,
    zona_id        BIGINT           NOT NULL REFERENCES zona (id) ON DELETE CASCADE,
    fecha_hora     TIMESTAMP        NOT NULL,
    probabilidad   DOUBLE PRECISION NOT NULL CHECK (probabilidad >= 0 AND probabilidad <= 1),
    riesgo         VARCHAR(10)      NOT NULL CHECK (riesgo IN ('BAJO', 'MEDIO', 'ALTO')),
    factores       VARCHAR(1000)    NOT NULL,
    version_modelo VARCHAR(20)      NOT NULL
);

CREATE INDEX ix_prediccion_ia_zona_fecha ON prediccion_ia (zona_id, fecha_hora DESC);
