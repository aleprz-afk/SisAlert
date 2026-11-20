-- V2: permitir textos más largos en los cuerpos de agua y borrar en cascada
-- las lecturas y alertas cuando se elimina una zona desde el CRUD.

ALTER TABLE zona ALTER COLUMN cuerpo_agua TYPE VARCHAR(255);

ALTER TABLE lectura DROP CONSTRAINT lectura_zona_id_fkey;
ALTER TABLE lectura ADD CONSTRAINT lectura_zona_id_fkey
    FOREIGN KEY (zona_id) REFERENCES zona (id) ON DELETE CASCADE;

ALTER TABLE alerta DROP CONSTRAINT alerta_zona_id_fkey;
ALTER TABLE alerta ADD CONSTRAINT alerta_zona_id_fkey
    FOREIGN KEY (zona_id) REFERENCES zona (id) ON DELETE CASCADE;
