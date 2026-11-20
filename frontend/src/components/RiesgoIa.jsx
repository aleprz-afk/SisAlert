// Predicción del servicio de IA para una zona: probabilidad de lluvia fuerte (>= 40 mm)
// en las próximas 24 horas, su banda de riesgo y las variables que más pesaron.
// La IA complementa al semáforo (que mide la lluvia de HOY): mira hacia adelante.

const BANDAS = {
  BAJO: { color: 'verde', texto: 'Bajo' },
  MEDIO: { color: 'amarillo', texto: 'Medio' },
  ALTO: { color: 'rojo', texto: 'Alto' },
}

function porcentaje(p) {
  if (p < 0.01) return '< 1 %'
  return `${Math.round(p * 100)} %`
}

export default function RiesgoIa({ riesgoIa, compacto = false }) {
  if (!riesgoIa) {
    return (
      <div className="riesgo-ia riesgo-ia--vacio">
        <span className="riesgo-ia-titulo">Pronóstico IA · próximas 24 h</span>
        <span className="riesgo-ia-nota">Sin predicción todavía (se calcula al sincronizar).</span>
      </div>
    )
  }

  const banda = BANDAS[riesgoIa.riesgo] ?? BANDAS.BAJO
  const factores = (riesgoIa.factores ?? []).slice(0, compacto ? 1 : 3)

  return (
    <div className="riesgo-ia">
      <div className="riesgo-ia-cabecera">
        <span className="riesgo-ia-titulo">Pronóstico IA · próximas 24 h</span>
        <span className={`badge badge--${banda.color}`}>Riesgo {banda.texto}</span>
      </div>
      <p className="riesgo-ia-probabilidad">
        <strong>{porcentaje(riesgoIa.probabilidad)}</strong> de probabilidad de lluvia fuerte (≥ 40 mm)
      </p>
      {factores.length > 0 && (
        <ul className="riesgo-ia-factores">
          {factores.map((f) => (
            <li key={f.descripcion}>
              <span className={f.efecto === 'aumenta' ? 'factor-sube' : 'factor-baja'} aria-hidden="true">
                {f.efecto === 'aumenta' ? '▲' : '▼'}
              </span>{' '}
              {f.descripcion} <span className="riesgo-ia-nota">({f.efecto} el riesgo)</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
