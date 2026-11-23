"""Entrena el modelo de riesgo de lluvia fuerte de SisAlert.

Pregunta que responde el modelo: dada la lluvia de la última semana en una zona y el
pronóstico meteorológico de las próximas 24 horas, ¿cuál es la probabilidad de que en las
PRÓXIMAS 24 HORAS caigan al menos UMBRAL_MM milímetros?
(40 mm = el umbral de PRECAUCIÓN de alertas-service.)

Datos (training/descargar_datos.py):
    data/lluvia_horaria.csv      lluvia que realmente cayó (reanálisis ERA5): historia y etiqueta
    data/pronostico_horario.csv  pronóstico emitido un día antes (Open-Meteo Previous Runs, desde 2024-02)

Evaluación temporal (nunca aleatoria, para no "ver el futuro"):
    - Desarrollo 2024-02 .. 2025-12, con validación cruzada por bloques: para cada trimestre
      desde 2024-10 se entrena con todo lo anterior y se predice ese trimestre (5 cortes,
      15 meses de predicciones fuera de muestra). Con ellas se elige el modelo.
    - Prueba final 2026-01 .. hoy: no se usa para ninguna decisión; solo se reporta.

Se comparan cinco modelos y se elige por precisión promedio (PR-AUC) en la validación cruzada:
    persistencia  -> regla simple: "si llovió mucho en las últimas 24 h, seguirá lloviendo"
    climatologia  -> frecuencia histórica de lluvia fuerte en ese mes
    pronostico    -> confiar directamente en los mm del pronóstico meteorológico
    logistica     -> regresión logística (interpretable)
    boosting      -> HistGradientBoosting (no lineal)

La versión 1 (solo lluvia pasada, 2015-2025) quedó registrada en
modelo/experimento_v1_solo_lluvia_pasada.json: sin el pronóstico el modelo apenas superaba
a la climatología, y por eso se agregó.

Uso (desde ai-service/):  python -m training.entrenar
Genera modelo/modelo.joblib y modelo/metricas.json.
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import sklearn
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (average_precision_score, brier_score_loss, f1_score,
                             precision_recall_curve, precision_score, recall_score, roc_auc_score)
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

from app import caracteristicas

RAIZ = Path(__file__).resolve().parent.parent
DATOS = RAIZ / "data" / "lluvia_horaria.csv"
PRONOSTICOS = RAIZ / "data" / "pronostico_horario.csv"
SALIDA = RAIZ / "modelo"

UMBRAL_MM = 40.0
HORIZONTE_H = 24
VERSION = "2.0.0"
INICIO_PRUEBA = "2026-01-01"
MULTIPLO_MEDIO = 3
MULTIPLO_ALTO = 6
# Inicio de cada bloque de validación cruzada (se entrena con todo lo anterior a cada uno).
BLOQUES = ["2024-10-01", "2025-01-01", "2025-04-01", "2025-07-01", "2025-10-01", INICIO_PRUEBA]

# Guatiquía Norte y Caño Grande Centro caen en la misma celda de la grilla de Open-Meteo
# (series idénticas); se entrena con las dos series distintas para no duplicar datos.
ESTACIONES_ENTRENAMIENTO = ["0035035100", "3502700090"]

NOMBRES_MODELOS = ["persistencia", "climatologia", "pronostico", "logistica", "boosting"]


def construir_dataset() -> pd.DataFrame:
    crudo = pd.read_csv(DATOS, dtype={"codigo_estacion": str}, parse_dates=["fecha_hora"])
    pronosticos = pd.read_csv(PRONOSTICOS, dtype={"codigo_estacion": str}, parse_dates=["fecha_hora"])
    partes = []
    for codigo in ESTACIONES_ENTRENAMIENTO:
        serie = crudo[crudo.codigo_estacion == codigo].sort_values("fecha_hora")
        lluvia = serie.precipitacion_mm.fillna(0).to_numpy()
        # Pronóstico alineado hora a hora con la lluvia observada (NaN antes de 2024-02).
        pron = (pronosticos[pronosticos.codigo_estacion == codigo]
                .set_index("fecha_hora")["pronostico_dia_antes"]
                .reindex(serie.fecha_hora).to_numpy())
        fechas = serie.fecha_hora.dt
        columnas = caracteristicas.calcular_lote(lluvia, pron, fechas.month.to_numpy(), fechas.hour.to_numpy())
        tabla = pd.DataFrame(columnas)
        tabla["fecha_hora"] = serie.fecha_hora.to_numpy()
        tabla["codigo_estacion"] = codigo
        # Etiqueta: lluvia acumulada en las 24 horas SIGUIENTES (t+1 .. t+24).
        futura = pd.Series(lluvia[::-1]).rolling(HORIZONTE_H).sum().to_numpy()[::-1]
        tabla["lluvia_futura_24h"] = np.append(futura[1:], np.nan)
        tabla["lluvia_24h_mm"] = np.expm1(tabla["lluvia_24h"])
        tabla["pronostico_24h_mm"] = np.expm1(tabla["pronostico_24h"])
        partes.append(tabla)
    datos = pd.concat(partes, ignore_index=True).dropna()
    datos["evento"] = (datos["lluvia_futura_24h"] >= UMBRAL_MM).astype(int)
    datos["mes"] = pd.to_datetime(datos["fecha_hora"]).dt.month
    return datos


def metricas(y: np.ndarray, puntaje: np.ndarray, umbral: float | None = None) -> dict:
    resultado = {
        "roc_auc": round(float(roc_auc_score(y, puntaje)), 4),
        "pr_auc": round(float(average_precision_score(y, puntaje)), 4),
    }
    if umbral is not None:
        prediccion = (puntaje >= umbral).astype(int)
        resultado.update({
            "precision": round(float(precision_score(y, prediccion, zero_division=0)), 4),
            "recall": round(float(recall_score(y, prediccion)), 4),
            "f1": round(float(f1_score(y, prediccion)), 4),
        })
    return resultado


def umbral_mejor_f1(y: np.ndarray, puntaje: np.ndarray) -> float:
    precision, recall, umbrales = precision_recall_curve(y, puntaje)
    f1 = 2 * precision * recall / np.clip(precision + recall, 1e-9, None)
    return float(umbrales[int(np.argmax(f1[:-1]))])


def crear_modelos() -> dict:
    return {
        # Regularización moderada (C=0.3): hay pocos eventos y muchas horas parecidas entre sí.
        "logistica": make_pipeline(StandardScaler(), LogisticRegression(C=0.3, max_iter=2000)),
        # Árboles poco profundos y hojas grandes para no memorizar los pocos eventos.
        "boosting": HistGradientBoostingClassifier(
            max_iter=150, learning_rate=0.05, max_depth=3, min_samples_leaf=200,
            l2_regularization=5.0, random_state=42),
    }


def puntajes(entrenamiento: pd.DataFrame, evaluar: pd.DataFrame) -> tuple[dict[str, np.ndarray], dict]:
    """Entrena con `entrenamiento` y devuelve el puntaje de cada modelo sobre `evaluar`."""
    clima_mes = entrenamiento.groupby("mes").evento.mean()
    resultado = {
        "persistencia": evaluar["lluvia_24h_mm"].to_numpy(),
        "pronostico": evaluar["pronostico_24h_mm"].to_numpy(),
        "climatologia": evaluar["mes"].map(clima_mes).fillna(entrenamiento.evento.mean()).to_numpy(),
    }
    modelos = crear_modelos()
    for nombre, modelo in modelos.items():
        modelo.fit(entrenamiento[caracteristicas.COLUMNAS].to_numpy(), entrenamiento.evento.to_numpy())
        resultado[nombre] = modelo.predict_proba(evaluar[caracteristicas.COLUMNAS].to_numpy())[:, 1]
    return resultado, modelos


def main() -> None:
    datos = construir_dataset()
    fecha = pd.to_datetime(datos["fecha_hora"])
    desarrollo = datos[fecha < INICIO_PRUEBA]
    prueba = datos[fecha >= INICIO_PRUEBA]
    print(f"Filas: desarrollo {len(desarrollo)} ({desarrollo.evento.mean():.2%} eventos), "
          f"prueba {len(prueba)} ({prueba.evento.mean():.2%} eventos)")

    # --- Validación cruzada por bloques temporales ------------------------------------------
    fecha_dev = pd.to_datetime(desarrollo["fecha_hora"])
    fuera_de_muestra = {n: [] for n in NOMBRES_MODELOS}
    etiquetas = []
    for inicio, fin in zip(BLOQUES[:-1], BLOQUES[1:]):
        entrenamiento = desarrollo[fecha_dev < inicio]
        bloque = desarrollo[(fecha_dev >= inicio) & (fecha_dev < fin)]
        valores, _ = puntajes(entrenamiento, bloque)
        for nombre in NOMBRES_MODELOS:
            fuera_de_muestra[nombre].append(valores[nombre])
        etiquetas.append(bloque.evento.to_numpy())
        print(f"  bloque {inicio}: entrena con {len(entrenamiento)} filas, evalúa {len(bloque)} "
              f"({int(bloque.evento.sum())} horas con evento)")
    y_cv = np.concatenate(etiquetas)
    cv = {n: np.concatenate(v) for n, v in fuera_de_muestra.items()}

    # --- Modelos finales: entrenados con todo el desarrollo, evaluados una sola vez en 2026 ---
    en_prueba, finales = puntajes(desarrollo, prueba)
    y_prueba = prueba.evento.to_numpy()

    comparacion = {}
    for nombre in NOMBRES_MODELOS:
        umbral = umbral_mejor_f1(y_cv, cv[nombre])
        comparacion[nombre] = {
            "validacion_cruzada": metricas(y_cv, cv[nombre], umbral),
            "prueba_2026": metricas(y_prueba, en_prueba[nombre], umbral),
        }
        if nombre in finales or nombre == "climatologia":
            comparacion[nombre]["validacion_cruzada"]["brier"] = round(float(brier_score_loss(y_cv, cv[nombre])), 4)
            comparacion[nombre]["prueba_2026"]["brier"] = round(float(brier_score_loss(y_prueba, en_prueba[nombre])), 4)
        print(f"{nombre:13s} CV     {comparacion[nombre]['validacion_cruzada']}")
        print(f"{'':13s} prueba {comparacion[nombre]['prueba_2026']}")

    elegido = max(finales, key=lambda n: comparacion[n]["validacion_cruzada"]["pr_auc"])
    # Se prefiere la regresión logística si queda a menos de 0,02 de PR-AUC: sus predicciones
    # se pueden explicar exactamente (aporte de cada variable), lo que importa en un sistema
    # de alertas a la comunidad.
    if elegido != "logistica" and (comparacion[elegido]["validacion_cruzada"]["pr_auc"]
                                  - comparacion["logistica"]["validacion_cruzada"]["pr_auc"]) < 0.02:
        elegido = "logistica"
    modelo = finales[elegido]

    # --- Bandas de riesgo: riesgo RELATIVO al normal --------------------------------------------
    # La lluvia fuerte es rara (≈3 % de las horas), así que una probabilidad de 10 % ya es
    # alta. Las bandas se definen como múltiplos de la tasa base del desarrollo:
    #   MEDIO: el riesgo es al menos 3 veces el normal · ALTO: al menos 6 veces el normal.
    tasa_base = float(desarrollo.evento.mean())
    umbral_medio = round(MULTIPLO_MEDIO * tasa_base, 3)
    umbral_alto = round(MULTIPLO_ALTO * tasa_base, 3)
    pp = en_prueba[elegido]

    fecha_prueba = pd.to_datetime(prueba["fecha_hora"])
    resumen = {
        "version": VERSION,
        "entrenado": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "pregunta": f"P(lluvia >= {UMBRAL_MM:.0f} mm en las próximas {HORIZONTE_H} h)",
        "umbral_mm": UMBRAL_MM,
        "horizonte_horas": HORIZONTE_H,
        "modelo_elegido": elegido,
        "variables": caracteristicas.COLUMNAS,
        "datos": {
            "fuente": ("Lluvia observada: Open-Meteo Historical Weather API (reanálisis ERA5). "
                       "Pronóstico: Open-Meteo Previous Runs API (pronóstico emitido un día antes)."),
            "estaciones": ESTACIONES_ENTRENAMIENTO,
            "desarrollo": f"{pd.to_datetime(desarrollo.fecha_hora).min():%Y-%m-%d} a 2025-12-31",
            "validacion_cruzada": f"{len(BLOQUES) - 1} bloques trimestrales desde {BLOQUES[0]}",
            "prueba": f"{fecha_prueba.min():%Y-%m-%d} a {fecha_prueba.max():%Y-%m-%d}",
            "filas": {"desarrollo": len(desarrollo), "prueba": len(prueba)},
            "horas_con_evento": {"desarrollo": int(desarrollo.evento.sum()), "prueba": int(prueba.evento.sum())},
        },
        "bandas_riesgo": {
            "MEDIO": round(umbral_medio, 4),
            "ALTO": round(umbral_alto, 4),
            "criterio": (f"Riesgo relativo: MEDIO >= {MULTIPLO_MEDIO}x y ALTO >= {MULTIPLO_ALTO}x la tasa base "
                         f"de eventos del desarrollo ({tasa_base:.3f}). Regla fijada de antemano, no ajustada con la prueba."),
            "validacion_cruzada_MEDIO": metricas(y_cv, cv[elegido], umbral_medio),
            "validacion_cruzada_ALTO": metricas(y_cv, cv[elegido], umbral_alto),
        },
        "prueba_2026_modelo_elegido": {
            "general": metricas(y_prueba, pp),
            "banda_MEDIO": metricas(y_prueba, pp, umbral_medio),
            "banda_ALTO": metricas(y_prueba, pp, umbral_alto),
            "brier": round(float(brier_score_loss(y_prueba, pp)), 4),
            "brier_climatologia": round(float(brier_score_loss(y_prueba, en_prueba["climatologia"])), 4),
            "tasa_base": round(float(y_prueba.mean()), 4),
        },
        "comparacion": comparacion,
        "versiones": {"scikit-learn": sklearn.__version__, "numpy": np.__version__, "pandas": pd.__version__},
    }

    SALIDA.mkdir(exist_ok=True)
    joblib.dump({"modelo": modelo, "tipo": elegido, "columnas": caracteristicas.COLUMNAS,
                 "bandas": {"MEDIO": umbral_medio, "ALTO": umbral_alto}, "resumen": resumen},
                SALIDA / "modelo.joblib")
    (SALIDA / "metricas.json").write_text(json.dumps(resumen, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nModelo elegido: {elegido}")
    print(json.dumps(resumen["bandas_riesgo"], ensure_ascii=False))
    print(json.dumps(resumen["prueba_2026_modelo_elegido"], ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
