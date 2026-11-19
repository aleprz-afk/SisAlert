-- =============================================================================
-- SISALERT — SCRIPT DE INICIALIZACIÓN PARA MYSQL WORKBENCH
-- Base de datos: Sistema de Alertas Tempranas de Crecientes (Villavicencio)
-- =============================================================================
-- INSTRUCCIONES PARA LEVANTAR EN CUALQUIER COMPUTADOR:
-- 1. Abre MySQL Workbench y entra a tu conexión local (ej. Local instance MySQL80).
-- 2. Ve a: File -> Open SQL Script... (o presiona Ctrl + Shift + O).
-- 3. Selecciona este archivo (init_mysql.sql).
-- 4. Haz clic en el ícono del Rayo (Execute) o presiona Ctrl + Shift + Enter.
-- 5. ¡Listo! La base de datos sisalert_db y sus tablas quedarán creadas y listas.
-- =============================================================================

-- 1. CREACIÓN DE LA BASE DE DATOS
CREATE DATABASE IF NOT EXISTS `sisalert_db`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `sisalert_db`;

-- 2. TABLA: ZONA
CREATE TABLE IF NOT EXISTS `zona` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `nombre` VARCHAR(100) NOT NULL UNIQUE,
    `cuerpo_agua` VARCHAR(100) NOT NULL,
    `comunidades` VARCHAR(255) NOT NULL,
    `codigo_estacion` VARCHAR(50) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. TABLA: LECTURA
CREATE TABLE IF NOT EXISTS `lectura` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `zona_id` BIGINT NOT NULL,
    `precipitacion_mm` DOUBLE NOT NULL,
    `fecha_hora` DATETIME(6) NOT NULL,
    `fuente` VARCHAR(50) NOT NULL,
    CONSTRAINT `fk_lectura_zona` FOREIGN KEY (`zona_id`) REFERENCES `zona` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. TABLA: ALERTA
CREATE TABLE IF NOT EXISTS `alerta` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `zona_id` BIGINT NOT NULL,
    `nivel` VARCHAR(20) NOT NULL,
    `mensaje` VARCHAR(255) NOT NULL,
    `fecha_hora` DATETIME(6) NOT NULL,
    `vigente` BOOLEAN NOT NULL,
    CONSTRAINT `fk_alerta_zona` FOREIGN KEY (`zona_id`) REFERENCES `zona` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. PRECARGA DE LAS TRES ZONAS REALES DE VILLAVICENCIO
-- (Usa INSERT IGNORE para que se pueda ejecutar varias veces sin duplicar ni arrojar error)
INSERT IGNORE INTO `zona` (`id`, `nombre`, `cuerpo_agua`, `comunidades`, `codigo_estacion`) VALUES
(1,
 'Zona Guatiquía Norte',
 'Río Guatiquía · Caño Parrado · Caño Gramalote · Quebrada Grande · Quebrada Honda · Caño Buque',
 'Barzal Alto, Barzal Bajo, Siete de Agosto, Villa Lorena, Mancera, La Nohora, El Triunfo',
 '0035035100'),

(2,
 'Zona Guayuriba Sur',
 'Río Guayuriba · Caño Rosablanca · Caño La Cuerera',
 'Apiay, Catumare, Playa Rica, La Esperanza, La Nohora Rural, Vanguardia',
 '3502700090'),

(3,
 'Zona Caño Grande Centro',
 'Caño Grande · Caño Maizaro · Quebrada La Unión',
 'Porvenir, Bello Horizonte, Villacentro, La Rosita, Marsella, Porfía',
 '0035025110');

-- 6. VERIFICACIÓN FINAL
SELECT 'Base de datos sisalert_db inicializada con éxito' AS Estado;
SELECT id, nombre, cuerpo_agua, codigo_estacion FROM `zona`;
