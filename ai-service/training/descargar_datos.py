"""Descarga la lluvia horaria histórica de las zonas de SisAlert desde Open-Meteo.

Fuente: Open-Meteo Historical Weather API (reanálisis ERA5 / ERA5-Land), gratuita y sin clave.
https://open-meteo.com/en/docs/historical-weather-api

Uso (desde ai-service/):
    python -m training.descargar_datos                 # 2015-01-01 .. hace 7 días
    python -m training.descargar_datos --desde 2018 --fin 2026-10-31

Genera:
  data/lluvia_horaria.csv     lluvia observada (reanálisis), la "verdad" para las etiquetas
  data/pronostico_horario.csv lluvia según el modelo de pronóstico (Previous Runs API, desde 2024-02):
                              precipitacion_mm      = mejor estimación de esa hora (último pronóstico)
                              pronostico_dia_antes  = lo que se pronosticaba para esa hora un día antes
La carpeta data/ no se sube al repositorio (se puede regenerar con este script).
"""

from __future__ import annotations

import argparse
import csv
import json
import time
import urllib.parse
import urllib.request
from pathlib import Path

# Mismas coordenadas que usa alertas-service (ClienteOpenMeteo / CoordenadasEstaciones).
ESTACIONES = {
    "0035035100": (4.1485, -73.6340),  # Zona Guatiquía Norte
    "3502700090": (4.0750, -73.6550),  # Zona Guayuriba Sur
    "0035025110": (4.1280, -73.6260),  # Zona Caño Grande Centro
}

URL = "https://archive-api.open-meteo.com/v1/archive"
URL_PRONOSTICOS = "https://previous-runs-api.open-meteo.com/v1/forecast"
DATA = Path(__file__).resolve().parent.parent / "data"
SALIDA = DATA / "lluvia_horaria.csv"
SALIDA_PRONOSTICOS = DATA / "pronostico_horario.csv"
INICIO_PRONOSTICOS = "2024-02-01"  # primer día con pronósticos archivados para Villavicencio


def _consultar(url_base: str, parametros: dict) -> dict:
    url = f"{url_base}?{urllib.parse.urlencode(parametros)}"
    peticion = urllib.request.Request(url, headers={"User-Agent": "SisAlert-entrenamiento/1.0"})
    for intento in range(4):
        try:
            with urllib.request.urlopen(peticion, timeout=60) as respuesta:
                return json.load(respuesta)["hourly"]
        except Exception as error:  # red o límite de peticiones: reintento con espera
            if intento == 3:
                raise
            espera = 5 * (intento + 1)
            print(f"  reintento en {espera}s ({error})")
            time.sleep(espera)
    raise RuntimeError("inalcanzable")


def descargar_anio(lat: float, lon: float, anio: int, fin: str) -> tuple[list[str], list[float | None]]:
    horas = _consultar(URL, {
        "latitude": f"{lat:.4f}", "longitude": f"{lon:.4f}",
        "start_date": f"{anio}-01-01", "end_date": min(f"{anio}-12-31", fin),
        "hourly": "precipitation", "timezone": "America/Bogota",
    })
    return horas["time"], horas["precipitation"]


def descargar_pronosticos(lat: float, lon: float, desde: str, hasta: str) -> dict:
    return _consultar(URL_PRONOSTICOS, {
        "latitude": f"{lat:.4f}", "longitude": f"{lon:.4f}",
        "start_date": desde, "end_date": hasta,
        "hourly": "precipitation,precipitation_previous_day1", "timezone": "America/Bogota",
    })


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--desde", type=int, default=2015)
    parser.add_argument("--hasta", type=int, default=2026)
    parser.add_argument("--fin", default=None, help="último día (AAAA-MM-DD); por defecto hace 7 días")
    args = parser.parse_args()
    import datetime as _dt
    fin = args.fin or (_dt.date.today() - _dt.timedelta(days=7)).isoformat()

    SALIDA.parent.mkdir(parents=True, exist_ok=True)
    filas = 0
    with SALIDA.open("w", newline="", encoding="utf-8") as archivo:
        escritor = csv.writer(archivo)
        escritor.writerow(["codigo_estacion", "fecha_hora", "precipitacion_mm"])
        for codigo, (lat, lon) in ESTACIONES.items():
            for anio in range(args.desde, args.hasta + 1):
                tiempos, lluvia = descargar_anio(lat, lon, anio, fin)
                for t, mm in zip(tiempos, lluvia):
                    escritor.writerow([codigo, t, "" if mm is None else mm])
                filas += len(tiempos)
                print(f"{codigo} {anio}: {len(tiempos)} horas")
                time.sleep(0.5)  # cortesía con la API gratuita
    print(f"Listo: {filas} filas en {SALIDA}")

    filas = 0
    with SALIDA_PRONOSTICOS.open("w", newline="", encoding="utf-8") as archivo:
        escritor = csv.writer(archivo)
        escritor.writerow(["codigo_estacion", "fecha_hora", "precipitacion_mm", "pronostico_dia_antes"])
        for codigo, (lat, lon) in ESTACIONES.items():
            inicio = _dt.date.fromisoformat(INICIO_PRONOSTICOS)
            final = _dt.date.fromisoformat(fin)
            while inicio <= final:
                tramo_fin = min(_dt.date(inicio.year, 12, 31), final)
                horas = descargar_pronosticos(lat, lon, inicio.isoformat(), tramo_fin.isoformat())
                for t, mm, antes in zip(horas["time"], horas["precipitation"], horas["precipitation_previous_day1"]):
                    escritor.writerow([codigo, t, "" if mm is None else mm, "" if antes is None else antes])
                filas += len(horas["time"])
                print(f"pronósticos {codigo} {inicio}..{tramo_fin}: {len(horas['time'])} horas")
                inicio = tramo_fin + _dt.timedelta(days=1)
                time.sleep(0.5)
    print(f"Listo: {filas} filas en {SALIDA_PRONOSTICOS}")


if __name__ == "__main__":
    main()
