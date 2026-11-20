"""SisAlert · servicio de IA (FastAPI).

Estima la probabilidad de lluvia fuerte (>= 40 mm) en las próximas 24 horas para una zona,
a partir de su lluvia de la última semana y del pronóstico meteorológico, y explica qué
variables pesaron más. Lo consume alertas-service después de cada sincronización.

Variables de entorno:
    AI_MODELO_PATH  ruta del modelo (por defecto modelo/modelo.joblib)
    AI_API_KEY      si se define, /predict exige el encabezado X-Api-Key con ese valor
                    (en Azure el servicio además es interno: no recibe tráfico de Internet)
"""

from __future__ import annotations

import hmac
import logging
import os
from datetime import datetime
from pathlib import Path

from fastapi import FastAPI, Header, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from app import caracteristicas
from app.modelo import ModeloRiesgo

log = logging.getLogger("sisalert.ia")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

RUTA_MODELO = Path(os.getenv("AI_MODELO_PATH", Path(__file__).resolve().parent.parent / "modelo" / "modelo.joblib"))
API_KEY = os.getenv("AI_API_KEY", "")

modelo = ModeloRiesgo(RUTA_MODELO)
log.info("Modelo %s (%s) cargado desde %s", modelo.version, modelo.tipo, RUTA_MODELO)

app = FastAPI(
    title="SisAlert · Servicio de IA",
    description="Probabilidad de lluvia fuerte en las próximas 24 horas, con explicación.",
    version=modelo.version,
)


class SolicitudPrediccion(BaseModel):
    serieHorariaMm: list[float] = Field(
        min_length=caracteristicas.HORAS_HISTORIA, max_length=caracteristicas.HORAS_HISTORIA,
        description=f"Lluvia (mm) de las últimas {caracteristicas.HORAS_HISTORIA} horas; la más antigua primero",
    )
    pronosticoHorarioMm: list[float] = Field(
        min_length=caracteristicas.HORAS_PRONOSTICO, max_length=caracteristicas.HORAS_PRONOSTICO,
        description=f"Lluvia pronosticada (mm) para las próximas {caracteristicas.HORAS_PRONOSTICO} horas",
    )
    fechaHora: datetime = Field(description="Hora local (America/Bogota) de la última hora de la serie")


class FactorRespuesta(BaseModel):
    variable: str
    descripcion: str
    aporte: float
    efecto: str


class RespuestaPrediccion(BaseModel):
    probabilidad: float
    riesgo: str
    factores: list[FactorRespuesta]
    versionModelo: str
    pregunta: str


def _error(estado: int, codigo: str, mensaje: str) -> JSONResponse:
    return JSONResponse(status_code=estado, content={"error": codigo, "mensaje": mensaje})


@app.exception_handler(RequestValidationError)
async def solicitud_invalida(_: Request, error: RequestValidationError) -> JSONResponse:
    detalle = error.errors()[0] if error.errors() else {}
    campo = ".".join(str(p) for p in detalle.get("loc", [])[1:]) or "cuerpo"
    return _error(422, "SOLICITUD_INVALIDA", f"{campo}: {detalle.get('msg', 'valor no válido')}")


@app.get("/health")
def health() -> dict:
    return {"estado": "ok", "versionModelo": modelo.version, "tipoModelo": modelo.tipo}


@app.get("/modelo")
def info_modelo() -> dict:
    """Ficha del modelo: datos, bandas de riesgo y métricas (para documentación y auditoría)."""
    return modelo.resumen


@app.post("/predict", response_model=RespuestaPrediccion)
def predecir(solicitud: SolicitudPrediccion, x_api_key: str | None = Header(default=None)):
    if API_KEY and not hmac.compare_digest(x_api_key or "", API_KEY):
        return _error(401, "NO_AUTORIZADO", "Falta o no es válido el encabezado X-Api-Key.")
    try:
        resultado = modelo.predecir(solicitud.serieHorariaMm, solicitud.pronosticoHorarioMm, solicitud.fechaHora)
    except ValueError as error:
        return _error(422, "SOLICITUD_INVALIDA", str(error))
    return RespuestaPrediccion(
        probabilidad=round(resultado.probabilidad, 4),
        riesgo=resultado.riesgo,
        factores=[FactorRespuesta(variable=f.variable, descripcion=f.descripcion, aporte=f.aporte,
                                  efecto="aumenta" if f.aporte > 0 else "disminuye")
                  for f in resultado.factores],
        versionModelo=modelo.version,
        pregunta=modelo.resumen["pregunta"],
    )
