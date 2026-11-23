-- =============================================================================
-- SisAlert · Usuarios de base de datos por servicio (mínimo privilegio)
-- =============================================================================
-- Se ejecuta UNA sola vez, con el usuario administrador del servidor PostgreSQL
-- (en Azure, el "admin" de Azure Database for PostgreSQL Flexible Server), antes del
-- primer despliegue. Crea:
--
--   alertas_app    -> solo puede usar y crear objetos en el esquema "alertas"
--   identidad_app  -> solo puede usar y crear objetos en el esquema "identidad"
--
-- Ninguno puede leer las tablas del otro ni crear objetos en "public". Así, si se
-- filtran las credenciales de un servicio, el daño queda limitado a su esquema.
--
-- Uso (las contraseñas se pasan como variables, nunca se escriben en el archivo):
--
--   psql "host=<servidor>.postgres.database.azure.com port=5432 dbname=sisalert user=<admin> sslmode=require" \
--        -v alertas_pass='<contraseña-alertas>' \
--        -v identidad_pass='<contraseña-identidad>' \
--        -f infra/db/usuarios-por-servicio.sql
--
-- Después, cada servicio se configura con su propio usuario:
--   alertas-service : DB_USER=alertas_app, DB_PASSWORD=<contraseña-alertas>
--   API identidad   : ConnectionStrings__Identity=...;Username=identidad_app;Password=<contraseña-identidad>;...
--                     Persistencia__EsquemaHistorial=identidad
-- =============================================================================

\set ON_ERROR_STOP on

CREATE ROLE alertas_app LOGIN PASSWORD :'alertas_pass';
CREATE ROLE identidad_app LOGIN PASSWORD :'identidad_pass';

-- Solo estos usuarios (y el administrador) pueden conectarse a la base.
REVOKE CONNECT ON DATABASE sisalert FROM PUBLIC;
GRANT CONNECT ON DATABASE sisalert TO alertas_app, identidad_app;

-- Nadie crea objetos en "public" (PostgreSQL 15+ ya lo hace así; se deja explícito).
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

-- Un esquema por servicio. Los crea el administrador y le da a cada servicio permiso
-- para usarlo y crear sus tablas (las migraciones de Flyway y EF Core lo hacen al arrancar).
CREATE SCHEMA IF NOT EXISTS alertas;
CREATE SCHEMA IF NOT EXISTS identidad;
REVOKE ALL ON SCHEMA alertas FROM PUBLIC;
REVOKE ALL ON SCHEMA identidad FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA alertas TO alertas_app;
GRANT USAGE, CREATE ON SCHEMA identidad TO identidad_app;

-- Cada servicio busca y crea sus objetos en su propio esquema por defecto.
ALTER ROLE alertas_app SET search_path = alertas;
ALTER ROLE identidad_app SET search_path = identidad;
