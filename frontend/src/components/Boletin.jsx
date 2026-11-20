// Boletín para la comunidad de una zona en alerta: el estado técnico (nivel, lluvia, riesgo
// de la IA) redactado en lenguaje sencillo. Lo redacta Gemini (IA generativa) o, si no está
// disponible, una plantilla fija. Solo existe para zonas en PRECAUCIÓN o PELIGRO.

export default function Boletin({ boletin }) {
  if (!boletin) return null
  const deGemini = boletin.fuente === 'gemini'
  return (
    <div className="boletin">
      <span className="boletin-titulo">📢 Boletín para la comunidad</span>
      <p className="boletin-texto">{boletin.texto}</p>
      <span className="boletin-fuente">
        {deGemini ? 'Redactado con IA generativa (Gemini) a partir de los datos del sistema' : 'Mensaje automático (plantilla)'}
      </span>
    </div>
  )
}
