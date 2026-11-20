#!/bin/sh
# Docker Compose: al crear la base por primera vez, crea los usuarios por servicio
# (alertas_app e identidad_app) con el mismo script que se usa en Azure.
# Lo ejecuta automáticamente la imagen oficial de PostgreSQL (docker-entrypoint-initdb.d).
set -e
psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v alertas_pass="$ALERTAS_DB_PASSWORD" \
     -v identidad_pass="$IDENTIDAD_DB_PASSWORD" \
     -f /sql/usuarios-por-servicio.sql
