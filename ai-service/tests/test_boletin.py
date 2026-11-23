import io
import json
import urllib.error

from fastapi.testclient import TestClient

from app import boletin, main

cliente = TestClient(main.app)

PELIGRO = {
    "zona": "Zona Guayuriba Sur",
    "cuerpoAgua": "Río Guayuriba · Caño Rosablanca",
    "comunidades": "Apiay, Catumare, Playa Rica",
    "nivel": "PELIGRO",
    "precipitacionMm": 92.0,
    "riesgoIa": {"riesgo": "ALTO", "probabilidad": 0.24,
                 "factores": [{"descripcion": "Lluvia pronosticada para las próximas 24 horas", "efecto": "aumenta"}]},
}


class RespuestaFalsa(io.BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *args):
        return False


def test_sin_clave_usa_la_plantilla(monkeypatch):
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    datos = cliente.post("/boletin", json=PELIGRO).json()
    assert datos["fuente"] == "plantilla"
    assert "Apiay" in datos["texto"] and "123" in datos["texto"] and "riesgo alto" in datos["texto"]


def test_con_clave_usa_gemini_y_limpia_el_texto(monkeypatch):
    monkeypatch.setenv("GEMINI_API_KEY", "clave-de-prueba")
    enviado = {}

    def falso_urlopen(peticion, timeout):
        enviado["clave"] = peticion.get_header("X-goog-api-key")
        enviado["cuerpo"] = json.loads(peticion.data)
        texto = "**Vecinos de Apiay**: el río Guayuriba puede crecer.\n\nAléjense de las orillas. Línea 123."
        return RespuestaFalsa(json.dumps({"candidates": [{"content": {"parts": [{"text": texto}]}}]}).encode())

    monkeypatch.setattr(boletin.urllib.request, "urlopen", falso_urlopen)
    datos = cliente.post("/boletin", json=PELIGRO).json()

    assert datos["fuente"] == "gemini"
    assert datos["texto"] == "Vecinos de Apiay: el río Guayuriba puede crecer. Aléjense de las orillas. Línea 123."
    assert enviado["clave"] == "clave-de-prueba"
    # Gemini recibe solo datos estructurados, como JSON, con las instrucciones aparte.
    assert json.loads(enviado["cuerpo"]["contents"][0]["parts"][0]["text"])["nivel"] == "PELIGRO"
    assert "systemInstruction" in enviado["cuerpo"]


def test_si_gemini_falla_o_sin_cuota_usa_la_plantilla(monkeypatch):
    monkeypatch.setenv("GEMINI_API_KEY", "clave-de-prueba")

    def cuota_agotada(peticion, timeout):
        raise urllib.error.HTTPError(peticion.full_url, 429, "Too Many Requests", {}, None)

    monkeypatch.setattr(boletin.urllib.request, "urlopen", cuota_agotada)
    assert cliente.post("/boletin", json=PELIGRO).json()["fuente"] == "plantilla"


def test_zona_normal_no_llama_a_gemini(monkeypatch):
    monkeypatch.setenv("GEMINI_API_KEY", "clave-de-prueba")

    def no_deberia_llamarse(*args, **kwargs):
        raise AssertionError("no debe llamar a Gemini para una zona en NORMAL")

    monkeypatch.setattr(boletin.urllib.request, "urlopen", no_deberia_llamarse)
    datos = cliente.post("/boletin", json={**PELIGRO, "nivel": "NORMAL", "riesgoIa": None}).json()
    assert datos["fuente"] == "plantilla"


def test_valida_la_entrada():
    respuesta = cliente.post("/boletin", json={**PELIGRO, "nivel": "CATASTROFE"})
    assert respuesta.status_code == 422
    assert respuesta.json()["error"] == "SOLICITUD_INVALIDA"
