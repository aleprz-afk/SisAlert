import { createContext } from 'react'

// Escenario de simulación elegido por el administrador (null = datos reales).
// Vive aparte para que SimulacionProvider.jsx solo exporte componentes (react-refresh).
export const SimulacionContext = createContext({ escenario: null, setEscenario: () => {} })

export const ESCENARIOS = [
  { valor: 'seca', texto: 'Simulación: época seca' },
  { valor: 'tormenta', texto: 'Simulación: tormenta' },
  { valor: 'creciente', texto: 'Simulación: creciente' },
]
