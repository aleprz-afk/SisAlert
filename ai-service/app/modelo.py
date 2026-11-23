"""Carga del modelo entrenado, predicción y explicación de cada resultado."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

import joblib
import numpy as np

from app import caracteristicas


@dataclass(frozen=True)
class Factor:
    variable: str
    descripcion: str
    aporte: float  # en log-odds: > 0 sube el riesgo, < 0 lo baja


@dataclass(frozen=True)
class Prediccion:
    probabilidad: float
    riesgo: str  # BAJO | MEDIO | ALTO
    factores: list[Factor]


class ModeloRiesgo:
    """Envuelve el artefacto generado por training/entrenar.py."""

    def __init__(self, ruta: Path):
        artefacto = joblib.load(ruta)
        if artefacto["columnas"] != caracteristicas.COLUMNAS:
            raise ValueError("El modelo se entrenó con otras variables; vuelva a entrenarlo")
        self._modelo = artefacto["modelo"]
        self.tipo: str = artefacto["tipo"]
        self.bandas: dict[str, float] = artefacto["bandas"]
        self.resumen: dict = artefacto["resumen"]
        self.version: str = self.resumen["version"]

    def predecir(self, serie_mm, pronostico_mm, fecha_hora: datetime, max_factores: int = 3) -> Prediccion:
        fila = caracteristicas.calcular(serie_mm, pronostico_mm, fecha_hora)
        x = np.array([[fila[c] for c in caracteristicas.COLUMNAS]])
        probabilidad = float(self._modelo.predict_proba(x)[0, 1])
        return Prediccion(
            probabilidad=probabilidad,
            riesgo=self._banda(probabilidad),
            factores=self._explicar(x)[:max_factores],
        )

    def _banda(self, probabilidad: float) -> str:
        if probabilidad >= self.bandas["ALTO"]:
            return "ALTO"
        if probabilidad >= self.bandas["MEDIO"]:
            return "MEDIO"
        return "BAJO"

    def _explicar(self, x: np.ndarray) -> list[Factor]:
        """Aporte de cada variable a la predicción.

        En la regresión logística la explicación es exacta: aporte = coeficiente × valor
        estandarizado, es decir, cuánto sube o baja el riesgo esa variable frente a una hora
        típica. Las variables que describen lo mismo (mes en seno y coseno) se suman.
        """
        if self.tipo != "logistica":
            return []
        escalador, regresion = self._modelo[0], self._modelo[-1]
        aportes = regresion.coef_[0] * escalador.transform(x)[0]
        agrupados: dict[str, float] = {}
        for variable, aporte in zip(caracteristicas.COLUMNAS, aportes):
            # mes_sin + mes_cos -> "mes", hora_sin + hora_cos -> "hora"
            grupo = variable.removesuffix("_sin").removesuffix("_cos")
            agrupados[grupo] = agrupados.get(grupo, 0.0) + float(aporte)
        factores = [
            Factor(variable=grupo, descripcion=caracteristicas.NOMBRES.get(grupo, caracteristicas.NOMBRES.get(grupo + "_sin", grupo)),
                   aporte=round(aporte, 3))
            for grupo, aporte in agrupados.items()
        ]
        return sorted(factores, key=lambda f: abs(f.aporte), reverse=True)
