"""Cálculo de características a partir de la lluvia horaria.

Este módulo es la ÚNICA definición de las variables del modelo: lo usan el entrenamiento
(training/entrenar.py) y el servicio (app/main.py). Así lo que ve el modelo en producción
se calcula exactamente igual que en el entrenamiento.

Entrada:
  - la lluvia de las últimas HORAS_HISTORIA horas (la más antigua primero, la hora actual al final),
  - el pronóstico de lluvia para las próximas HORAS_PRONOSTICO horas,
  - la fecha-hora local de la hora actual.
"""

from __future__ import annotations

import math
from datetime import datetime

import numpy as np

# Una semana de historia: suficiente para medir qué tan saturado está el suelo.
HORAS_HISTORIA = 168
# Pronóstico meteorológico de las próximas 24 horas (el mismo horizonte que se predice).
HORAS_PRONOSTICO = 24

# Nombre interno -> nombre legible (se usa para explicar las predicciones).
NOMBRES = {
    "lluvia_1h": "Lluvia de la última hora",
    "lluvia_3h": "Lluvia de las últimas 3 horas",
    "lluvia_6h": "Lluvia de las últimas 6 horas",
    "lluvia_12h": "Lluvia de las últimas 12 horas",
    "lluvia_24h": "Lluvia de las últimas 24 horas",
    "lluvia_48h": "Lluvia de las últimas 48 horas",
    "lluvia_72h": "Lluvia de los últimos 3 días",
    "lluvia_7d": "Lluvia de los últimos 7 días (saturación del suelo)",
    "intensidad_max_24h": "Intensidad máxima en 24 horas",
    "horas_con_lluvia_24h": "Horas con lluvia en el último día",
    "pronostico_6h": "Lluvia pronosticada para las próximas 6 horas",
    "pronostico_24h": "Lluvia pronosticada para las próximas 24 horas",
    "pronostico_max_24h": "Intensidad máxima pronosticada en 24 horas",
    "mes_sin": "Época del año",
    "mes_cos": "Época del año",
    "hora_sin": "Hora del día",
    "hora_cos": "Hora del día",
}

COLUMNAS = list(NOMBRES)

# Variables de lluvia acumulada: se transforman con log(1 + x) porque la lluvia es muy
# asimétrica (muchas horas en 0 y pocas con valores altos).
_ACUMULADOS = {"lluvia_1h": 1, "lluvia_3h": 3, "lluvia_6h": 6, "lluvia_12h": 12,
               "lluvia_24h": 24, "lluvia_48h": 48, "lluvia_72h": 72, "lluvia_7d": 168}


def _validar(serie: np.ndarray, horas: int, que: str) -> None:
    if serie.shape[0] != horas:
        raise ValueError(f"Se esperaban {horas} horas de {que} y llegaron {serie.shape[0]}")
    if np.isnan(serie).any() or (serie < 0).any():
        raise ValueError(f"La serie de {que} no puede tener valores vacíos ni negativos")


def calcular(serie_mm: list[float] | np.ndarray, pronostico_mm: list[float] | np.ndarray,
             fecha_hora: datetime) -> dict[str, float]:
    """Características de un instante a partir de su historia de lluvia y del pronóstico."""
    serie = np.asarray(serie_mm, dtype=float)
    pronostico = np.asarray(pronostico_mm, dtype=float)
    _validar(serie, HORAS_HISTORIA, "lluvia")
    _validar(pronostico, HORAS_PRONOSTICO, "pronóstico")

    fila: dict[str, float] = {}
    for nombre, horas in _ACUMULADOS.items():
        fila[nombre] = math.log1p(float(serie[-horas:].sum()))
    ultimo_dia = serie[-24:]
    fila["intensidad_max_24h"] = math.log1p(float(ultimo_dia.max()))
    fila["horas_con_lluvia_24h"] = float((ultimo_dia >= 0.1).sum())
    fila["pronostico_6h"] = math.log1p(float(pronostico[:6].sum()))
    fila["pronostico_24h"] = math.log1p(float(pronostico.sum()))
    fila["pronostico_max_24h"] = math.log1p(float(pronostico.max()))
    fila.update(_ciclicas(fecha_hora.month, fecha_hora.hour))
    return {nombre: fila[nombre] for nombre in COLUMNAS}


def calcular_lote(serie_mm: np.ndarray, pronostico_dia_antes_mm: np.ndarray,
                  meses: np.ndarray, horas: np.ndarray) -> dict[str, np.ndarray]:
    """Versión vectorizada para el entrenamiento: características de TODAS las horas de una
    serie larga. Produce los mismos valores que calcular() aplicada hora por hora.

    pronostico_dia_antes_mm[i] = lluvia que se pronosticaba para la hora i un día antes; el
    pronóstico "visto" en la hora t son las horas t+1 .. t+24 de esa serie. Quedan en NaN las
    horas sin historia completa o sin pronóstico completo."""
    serie = np.asarray(serie_mm, dtype=float)
    acumulada = np.concatenate([[0.0], np.cumsum(serie)])
    n = serie.shape[0]
    fin = np.arange(1, n + 1)

    def suma_ultimas(horas_ventana: int) -> np.ndarray:
        inicio = fin - horas_ventana
        valores = acumulada[fin] - acumulada[np.clip(inicio, 0, None)]
        valores[inicio < 0] = np.nan
        return valores

    columnas: dict[str, np.ndarray] = {}
    for nombre, horas_ventana in _ACUMULADOS.items():
        # clip evita -0.000001 por redondeo de la suma acumulada
        columnas[nombre] = np.log1p(np.clip(suma_ultimas(horas_ventana), 0, None))
    ventanas = np.lib.stride_tricks.sliding_window_view(serie, 24)
    maximo = np.full(n, np.nan)
    con_lluvia = np.full(n, np.nan)
    maximo[23:] = ventanas.max(axis=1)
    con_lluvia[23:] = (ventanas >= 0.1).sum(axis=1)
    columnas["intensidad_max_24h"] = np.log1p(maximo)
    columnas["horas_con_lluvia_24h"] = con_lluvia

    futuro = np.asarray(pronostico_dia_antes_mm, dtype=float)
    pron6 = np.full(n, np.nan)
    pron24 = np.full(n, np.nan)
    pron_max = np.full(n, np.nan)
    if n > HORAS_PRONOSTICO:
        ventanas_futuro = np.lib.stride_tricks.sliding_window_view(futuro[1:], HORAS_PRONOSTICO)
        pron24[:ventanas_futuro.shape[0]] = ventanas_futuro.sum(axis=1)
        pron6[:ventanas_futuro.shape[0]] = ventanas_futuro[:, :6].sum(axis=1)
        pron_max[:ventanas_futuro.shape[0]] = ventanas_futuro.max(axis=1)
    columnas["pronostico_6h"] = np.log1p(pron6)
    columnas["pronostico_24h"] = np.log1p(pron24)
    columnas["pronostico_max_24h"] = np.log1p(pron_max)
    ciclicas = _ciclicas(np.asarray(meses), np.asarray(horas))
    columnas.update(ciclicas)

    sin_historia = np.arange(n) < HORAS_HISTORIA - 1
    for valores in columnas.values():
        valores[sin_historia] = np.nan
    return {nombre: columnas[nombre] for nombre in COLUMNAS}


def _ciclicas(mes, hora):
    """Mes y hora como ángulos: diciembre queda cerca de enero y las 23 h cerca de las 0 h."""
    return {
        "mes_sin": np.sin(2 * np.pi * (np.asarray(mes) - 1) / 12) * 1.0,
        "mes_cos": np.cos(2 * np.pi * (np.asarray(mes) - 1) / 12) * 1.0,
        "hora_sin": np.sin(2 * np.pi * np.asarray(hora) / 24) * 1.0,
        "hora_cos": np.cos(2 * np.pi * np.asarray(hora) / 24) * 1.0,
    } if isinstance(mes, np.ndarray) else {
        "mes_sin": math.sin(2 * math.pi * (mes - 1) / 12),
        "mes_cos": math.cos(2 * math.pi * (mes - 1) / 12),
        "hora_sin": math.sin(2 * math.pi * hora / 24),
        "hora_cos": math.cos(2 * math.pi * hora / 24),
    }
