# ai-service: riesgo de lluvia fuerte con IA

Microservicio en Python (FastAPI + scikit-learn). Estima la probabilidad de que en las **próximas 24 horas** caigan **40 mm o más** en una zona de Villavicencio y explica qué variables pesaron más. Lo consume `alertas-service` en cada sincronización.

La ficha completa del modelo (datos, evaluación, métricas y limitaciones) está en [`docs/ia.md`](../docs/ia.md).

## Levantarlo en local

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\pip install -r requirements.txt
.\.venv\Scripts\python -m uvicorn app.main:app --port 8000
```

- Documentación interactiva: http://localhost:8000/docs
- alertas-service, en modo dev, lo busca en `http://localhost:8000`. Si no está corriendo, la sincronización sigue funcionando sin predicción.

## Endpoints

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/health` | Estado y versión del modelo |
| GET | `/modelo` | Ficha del modelo: datos, bandas de riesgo y métricas |
| POST | `/predict` | Predicción para una zona |
| POST | `/boletin` | Boletín en lenguaje sencillo para las comunidades de una zona en alerta (Gemini o plantilla) |

Ejemplo de `POST /predict`:

```json
{
  "serieHorariaMm": [0.0, 0.0, "...168 valores: lluvia de la última semana, la más antigua primero..."],
  "pronosticoHorarioMm": [1.5, 2.0, "...24 valores: pronóstico de las próximas 24 horas..."],
  "fechaHora": "2026-11-20T10:00:00"
}
```

Respuesta:

```json
{
  "probabilidad": 0.24,
  "riesgo": "ALTO",
  "factores": [
    {"variable": "pronostico_24h", "descripcion": "Lluvia pronosticada para las próximas 24 horas", "aporte": 1.9, "efecto": "aumenta"},
    {"variable": "mes", "descripcion": "Época del año", "aporte": -0.8, "efecto": "disminuye"},
    {"variable": "lluvia_24h", "descripcion": "Lluvia de las últimas 24 horas", "aporte": 0.7, "efecto": "aumenta"}
  ],
  "versionModelo": "2.0.0",
  "pregunta": "P(lluvia >= 40 mm en las próximas 24 h)"
}
```

Los errores responden con `{"error": "CODIGO", "mensaje": "texto"}`, igual que el resto del proyecto.

## Variables de entorno

| Variable | Para qué |
|---|---|
| `AI_API_KEY` | Si se define, `/predict` exige el encabezado `X-Api-Key` con ese valor. alertas-service lo envía con su propia `AI_API_KEY` |
| `AI_MODELO_PATH` | Ruta del modelo. Por defecto `modelo/modelo.joblib` |
| `GEMINI_API_KEY` | Clave de Google AI Studio para redactar los boletines. Sin ella se usa una plantilla fija |
| `GEMINI_MODEL` | Modelo de Gemini. Por defecto `gemini-flash-lite-latest` |

En Azure el contenedor de IA es **interno**: solo alertas-service lo alcanza y no recibe tráfico de Internet.

## Reentrenar

```powershell
.\.venv\Scripts\pip install -r requirements-dev.txt
.\.venv\Scripts\python -m training.descargar_datos   # crea data/ (no se sube al repositorio)
.\.venv\Scripts\python -m training.entrenar           # crea modelo/modelo.joblib y modelo/metricas.json
.\.venv\Scripts\python -m pytest                      # 16 pruebas
```

`modelo/modelo.joblib` (unos 5 KB) sí se sube al repositorio. Así el servicio arranca sin reentrenar y queda versionado junto con el código que lo usa. `scikit-learn` y `numpy` deben ser las mismas versiones de `requirements.txt` con las que se entrenó.

## Estructura

```
ai-service/
├── app/
│   ├── caracteristicas.py   variables del modelo (las mismas al entrenar y al servir)
│   ├── modelo.py            carga, predicción, bandas de riesgo y explicación
│   └── main.py              API FastAPI
├── training/
│   ├── descargar_datos.py   lluvia histórica y pronósticos archivados de Open-Meteo
│   └── entrenar.py          comparación de 5 modelos, validación temporal y métricas
├── modelo/
│   ├── modelo.joblib                            modelo entrenado (v2)
│   ├── metricas.json                            métricas del modelo actual
│   └── experimento_v1_solo_lluvia_pasada.json   iteración anterior, sin pronóstico
└── tests/                   pruebas de variables y de la API
```
