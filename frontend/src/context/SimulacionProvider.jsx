import { useState } from 'react'
import { useAuth } from './useAuth'
import { ESCENARIOS, SimulacionContext } from './estadoSimulacion'

const CLAVE = 'simulacion'

// Modo simulación del administrador.
// - Solo existe para Admin: para cualquier otro usuario el escenario es siempre null y la
//   aplicación muestra los datos reales (Open-Meteo + IA), igual que antes.
// - Es solo de esta pestaña (sessionStorage): no cambia lo que ven los demás usuarios ni
//   guarda nada en la base de datos; el backend lo calcula al vuelo en /api/simulacion.
export function SimulacionProvider({ children }) {
  const { esAdmin } = useAuth()
  const [elegido, setElegido] = useState(() => sessionStorage.getItem(CLAVE))

  function setEscenario(valor) {
    if (valor) sessionStorage.setItem(CLAVE, valor)
    else sessionStorage.removeItem(CLAVE)
    setElegido(valor || null)
  }

  const escenario = esAdmin && ESCENARIOS.some((e) => e.valor === elegido) ? elegido : null

  return (
    <SimulacionContext.Provider value={{ escenario, setEscenario }}>
      {children}
    </SimulacionContext.Provider>
  )
}
