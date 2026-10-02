// Aviso visible cuando el frontend está sirviéndose de datos de demostración
// porque el alertas-service no responde. Desaparece solo en cuanto el servicio real contesta.

import { enModoDemostracion, mockForzado, reconectarServidor } from '../api/alertasApi'

export default function ChipModoDemo({ onReintentar }) {
  if (!enModoDemostracion()) return null

  return (
    <span className="chip-demo">
      {mockForzado
        ? 'Modo demostración (VITE_MOCK_ALERTAS=true)'
        : 'Datos de demostración · alertas-service sin conexión'}
      {!mockForzado && (
        <button
          type="button"
          className="btn-chip"
          onClick={() => {
            reconectarServidor()
            onReintentar?.()
          }}
        >
          Reintentar
        </button>
      )}
    </span>
  )
}
