-- Esquema inicial del alertas-service (PostgreSQL).
-- Los nombres siguen la estrategia de Hibernate: snake_case y sin pluralizar.
-- Versiones futuras: V2__..., V3__... (no editar este archivo una vez aplicado).

CREATE TABLE zona (
    id              BIGSERIAL PRIMARY KEY,
    nombre          VARCHAR(100) NOT NULL UNIQUE,
    cuerpo_agua     VARCHAR(100) NOT NULL,
    comunidades     VARCHAR(255) NOT NULL,
    codigo_estacion VARCHAR(50)  NOT NULL
);

CREATE TABLE lectura (
    id               BIGSERIAL PRIMARY KEY,
    zona_id          BIGINT           NOT NULL REFERENCES zona (id),
    precipitacion_mm DOUBLE PRECISION NOT NULL,
    fecha_hora       TIMESTAMP        NOT NULL,
    fuente           VARCHAR(50)      NOT NULL
);

CREATE INDEX ix_lectura_zona_fecha ON lectura (zona_id, fecha_hora DESC);
CREATE INDEX ix_lectura_fecha ON lectura (fecha_hora DESC);

CREATE TABLE alerta (
    id         BIGSERIAL PRIMARY KEY,
    zona_id    BIGINT       NOT NULL REFERENCES zona (id),
    nivel      VARCHAR(20)  NOT NULL,
    mensaje    VARCHAR(255) NOT NULL,
    fecha_hora TIMESTAMP    NOT NULL,
    vigente    BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX ix_alerta_zona_vigente ON alerta (zona_id, vigente);
CREATE INDEX ix_alerta_fecha ON alerta (fecha_hora DESC);

-- Regla de negocio: como máximo una alerta vigente por zona.
CREATE UNIQUE INDEX ux_alerta_vigente ON alerta (zona_id) WHERE vigente;
