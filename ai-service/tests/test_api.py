from fastapi.testclient import TestClient

from app import caracteristicas as c
from app import main

cliente = TestClient(main.app)

SECO = {"serieHorariaMm": [0.0] * c.HORAS_HISTORIA, "pronosticoHorarioMm": [0.0] * c.HORAS_PRONOSTICO,
        "fechaHora": "2026-01-15T10:00:00"}
TORMENTA = {"serieHorariaMm": [0.5] * 120 + [4.0] * 48, "pronosticoHorarioMm": [6.0] * c.HORAS_PRONOSTICO,
            "fechaHora": "2026-05-15T18:00:00"}


def test_health():
    respuesta = cliente.get("/health")
    assert respuesta.status_code == 200
    assert respuesta.json()["estado"] == "ok"


def test_ficha_del_modelo_trae_metricas_y_bandas():
    ficha = cliente.get("/modelo").json()
    assert {"MEDIO", "ALTO"} <= set(ficha["bandas_riesgo"])
    assert "prueba_2026_modelo_elegido" in ficha


def test_tormenta_tiene_mas_riesgo_que_tiempo_seco():
    seco = cliente.post("/predict", json=SECO).json()
    tormenta = cliente.post("/predict", json=TORMENTA).json()
    assert 0 <= seco["probabilidad"] < tormenta["probabilidad"] <= 1
    assert seco["riesgo"] == "BAJO"
    assert tormenta["riesgo"] == "ALTO"


def test_explica_con_los_factores_que_mas_pesan():
    factores = cliente.post("/predict", json=TORMENTA).json()["factores"]
    assert len(factores) == 3
    assert all(f["efecto"] in ("aumenta", "disminuye") and f["descripcion"] for f in factores)
    assert abs(factores[0]["aporte"]) >= abs(factores[-1]["aporte"])


def test_serie_de_largo_incorrecto_responde_422_con_formato_del_proyecto():
    respuesta = cliente.post("/predict", json={**SECO, "serieHorariaMm": [0.0] * 5})
    assert respuesta.status_code == 422
    assert respuesta.json()["error"] == "SOLICITUD_INVALIDA"


def test_con_api_key_configurada_exige_el_encabezado(monkeypatch):
    monkeypatch.setattr(main, "API_KEY", "secreto-de-prueba")
    assert cliente.post("/predict", json=SECO).status_code == 401
    assert cliente.post("/predict", json=SECO, headers={"X-Api-Key": "otra"}).status_code == 401
    assert cliente.post("/predict", json=SECO, headers={"X-Api-Key": "secreto-de-prueba"}).status_code == 200
