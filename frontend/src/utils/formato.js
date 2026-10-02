// fechaHora llega como LocalDateTime de Java (sin zona): se interpreta como hora local.
export function fechaLocal(valor) {
  if (!valor) return '—'
  const fecha = new Date(valor)
  if (Number.isNaN(fecha.getTime())) return '—'
  return fecha.toLocaleString('es-CO', { dateStyle: 'short', timeStyle: 'short' })
}

export function milimetros(valor) {
  return valor == null ? '—' : `${Number(valor).toFixed(1)} mm`
}