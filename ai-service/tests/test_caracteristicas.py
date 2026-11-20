from datetime import datetime

import numpy as np
import pytest

from app import caracteristicas as c


def test_el_lote_del_entrenamiento_coincide_con_el_calculo_del_servicio():
    """Si estas dos funciones divergen, el modelo vería en producción algo distinto a lo
    que vio al entrenar. Se comparan sobre una serie larga aleatoria."""
    rng = np.random.default_rng(7)
    n = 400
    lluvia = np.where(rng.random(n) < 0.2, rng.gamma(1.5, 4.0, n), 0.0)
    pronostico = np.where(rng.random(n) < 0.25, rng.gamma(1.2, 3.0, n), 0.0)
    fechas = np.array([datetime(2026, 1, 1) + np.timedelta64(i, "h").astype(object) for i in range(n)])
    lote = c.calcular_lote(lluvia, pronostico, np.array([f.month for f in fechas]), np.array([f.hour for f in fechas]))

    for t in [c.HORAS_HISTORIA - 1, 250, n - c.HORAS_PRONOSTICO - 1]:
        historia = lluvia[t - c.HORAS_HISTORIA + 1: t + 1]
        futuro = pronostico[t + 1: t + 1 + c.HORAS_PRONOSTICO]
        fila = c.calcular(historia, futuro, fechas[t])
        for columna in c.COLUMNAS:
            assert fila[columna] == pytest.approx(lote[columna][t], abs=1e-9), columna


def test_sin_historia_completa_queda_vacio():
    lote = c.calcular_lote(np.zeros(200), np.zeros(200), np.ones(200), np.zeros(200))
    assert np.isnan(lote["lluvia_7d"][c.HORAS_HISTORIA - 2])
    assert np.isnan(lote["pronostico_24h"][200 - c.HORAS_PRONOSTICO])


@pytest.mark.parametrize("historia,pronostico", [
    ([0.0] * 10, [0.0] * c.HORAS_PRONOSTICO),
    ([0.0] * c.HORAS_HISTORIA, [0.0] * 5),
    ([-1.0] + [0.0] * (c.HORAS_HISTORIA - 1), [0.0] * c.HORAS_PRONOSTICO),
])
def test_rechaza_series_invalidas(historia, pronostico):
    with pytest.raises(ValueError):
        c.calcular(historia, pronostico, datetime(2026, 5, 1, 12))
