# syntax=docker/dockerfile:1
# ai-service (FastAPI + scikit-learn)
# Misma versión de Python con la que se entrenó modelo/modelo.joblib.
FROM python:3.14-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1
WORKDIR /app

RUN useradd --system --create-home sisalert
COPY requirements.txt .
RUN pip install -r requirements.txt
# Solo lo necesario para servir: la app y el modelo entrenado (sin datos ni scripts de entrenamiento).
COPY app app
COPY modelo modelo
USER sisalert

ENV PORT=8000
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD python -c "import os, urllib.request; urllib.request.urlopen(f'http://localhost:{os.environ[\"PORT\"]}/health', timeout=4)" || exit 1

# --proxy-headers: detrás del ingreso de Azure. Servicio interno: no recibe tráfico de Internet.
CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT} --proxy-headers --forwarded-allow-ips='*'"]
