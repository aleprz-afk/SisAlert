import { useContext } from 'react'
import { AuthContext } from './sesionContext'

export function useAuth() {
  return useContext(AuthContext)
}
