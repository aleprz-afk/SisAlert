import { useContext } from 'react'
import { SimulacionContext } from './estadoSimulacion'

export function useSimulacion() {
  return useContext(SimulacionContext)
}
