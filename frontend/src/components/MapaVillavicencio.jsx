import { useEffect, useRef, useState } from 'react'
import { milimetros } from '../utils/formato'

const COORDENADAS_ZONAS = {
  // Puntos de monitoreo georreferenciados en los sectores urbanos de riesgo (Villavicencio)
  1: { lat: 4.1485, lng: -73.634, nombre: 'Guatiquía Norte (Barzal / Siete de Agosto)' },
  2: { lat: 4.075, lng: -73.655, nombre: 'Guayuriba Sur (Catumare / Porfía)' },
  3: { lat: 4.128, lng: -73.626, nombre: 'Caño Grande / Maizaro (Villacentro / Porvenir)' },
}

const COLORES_HEX = {
  verde: '#2e7d32',
  amarillo: '#f9a825',
  rojo: '#c62828',
  'sin-datos': '#78909c',
}

export default function MapaVillavicencio({ zonas = [] }) {
  const mapContainerRef = useRef(null)
  const mapInstanceRef = useRef(null)
  const markersRef = useRef([])
  const [modoVector, setModoVector] = useState(false)
  const [zonaSeleccionada, setZonaSeleccionada] = useState(null)
  
  // Verificación reactiva si Leaflet está en window
  const leafletDisponible = typeof window !== 'undefined' && Boolean(window.L)

  // Inicializar mapa de Leaflet
  useEffect(() => {
    if (!leafletDisponible || modoVector || !mapContainerRef.current) return

    const L = window.L

    // Si ya existe instancia previa, destruirla antes de recrear
    if (mapInstanceRef.current) {
      mapInstanceRef.current.remove()
      mapInstanceRef.current = null
    }

    try {
      const map = L.map(mapContainerRef.current, {
        center: [4.118, -73.638],
        zoom: 13,
        zoomControl: true,
        scrollWheelZoom: false,
      })

      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> · SisAlert',
        maxZoom: 18,
      }).addTo(map)

      mapInstanceRef.current = map
    } catch (e) {
      console.warn('[MapaVillavicencio] No se pudo inicializar mapa Leaflet:', e)
    }

    return () => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove()
        mapInstanceRef.current = null
      }
    }
  }, [leafletDisponible, modoVector])

  // Actualizar marcadores sobre el mapa cuando cambian las zonas
  useEffect(() => {
    if (!mapInstanceRef.current || modoVector || !window.L) return

    const L = window.L
    const map = mapInstanceRef.current

    // Limpiar marcadores viejos
    markersRef.current.forEach((m) => m.remove())
    markersRef.current = []

    zonas.forEach((z) => {
      const coords = COORDENADAS_ZONAS[z.zonaId]
      if (!coords) return

      const colorHex = COLORES_HEX[z.color] || COLORES_HEX['sin-datos']
      const esPeligro = z.color === 'rojo'

      // Icono HTML personalizado con aro pulsante
      const customIcon = L.divIcon({
        className: 'custom-map-pin',
        html: `
          <div class="pin-marker ${esPeligro ? 'pin-marker--peligro' : ''}" style="background-color: ${colorHex};">
            <span class="pin-pulso" style="border-color: ${colorHex};"></span>
            <span class="pin-punto"></span>
          </div>
        `,
        iconSize: [28, 28],
        iconAnchor: [14, 14],
      })

      const popupHtml = `
        <div class="map-popup">
          <span class="badge badge--${z.color}">${z.etiqueta || 'Sin datos'}</span>
          <h4 style="margin: 0.3rem 0 0.2rem;">${z.zona}</h4>
          <p style="margin: 0 0 0.4rem; font-size: 0.85rem; color: #555;"><strong>Cuenca:</strong> ${z.cuerpoAgua}</p>
          <div style="background: #f1f5f9; padding: 0.4rem 0.6rem; border-radius: 6px; margin-bottom: 0.4rem;">
            <strong>Lluvia acumulada:</strong> ${milimetros(z.precipitacionMm)}
          </div>
          <p style="margin: 0; font-size: 0.8rem; color: #64748b;"><strong>Comunidades:</strong> ${z.comunidades}</p>
        </div>
      `

      const marker = L.marker([coords.lat, coords.lng], { icon: customIcon })
        .addTo(map)
        .bindPopup(popupHtml)

      markersRef.current.push(marker)
    })
  }, [zonas, modoVector])

  return (
    <div className="seccion-mapa">
      <div className="mapa-cabecera">
        <div>
          <h3 className="mapa-titulo">📍 Georreferenciación de Cuencas en Villavicencio</h3>
          <p className="mapa-subtitulo">
            Estaciones telemétricas con indicador de semáforo hidrológico en tiempo real
          </p>
        </div>
        {leafletDisponible && (
          <button
            type="button"
            className="btn-modo-mapa"
            onClick={() => setModoVector(!modoVector)}
          >
            {modoVector ? '🗺️ Ver Mapa Cartográfico (OpenStreetMap)' : '🌊 Ver Red Hidrográfica (Vectorial)'}
          </button>
        )}
      </div>

      {!modoVector && leafletDisponible ? (
        <div className="mapa-contenedor" ref={mapContainerRef} />
      ) : (
        /* Vista de Respaldo: Red Hidrográfica Vectorial interactiva (Funciona 100% Offline) */
        <div className="mapa-vectorial">
          <div className="mapa-vectorial-svg-wrap">
            <svg viewBox="0 0 800 450" className="svg-hidrografico" aria-label="Red Hidrográfica Villavicencio">
              <defs>
                <linearGradient id="cordillera" x1="0%" y1="0%" x2="100%" y2="100%">
                  <stop offset="0%" stopColor="#cfd8dc" />
                  <stop offset="100%" stopColor="#eceff1" />
                </linearGradient>
              </defs>

              {/* Fondo topográfico representativo */}
              <rect width="800" height="450" fill="#f8fafc" rx="10" />
              <path d="M 0,0 L 320,0 L 260,280 L 0,380 Z" fill="url(#cordillera)" opacity="0.6" />
              <text x="30" y="40" fill="#90a4ae" fontSize="13" fontWeight="bold">CORDILLERA ORIENTAL (Zona Alta)</text>
              <text x="450" y="420" fill="#94a3b8" fontSize="13">PLANICIE DE LOS LLANOS (Zona Baja)</text>

              {/* Río Guatiquía (Norte) */}
              <path d="M 50,70 Q 220,110 380,120 T 780,150" fill="none" stroke="#0284c7" strokeWidth="8" strokeLinecap="round" />
              <text x="600" y="140" fill="#0369a1" fontSize="13" fontWeight="bold">Río Guatiquía</text>

              {/* Caño Maizaro / Caño Grande (Centro) */}
              <path d="M 120,200 Q 280,220 450,250 T 780,270" fill="none" stroke="#38bdf8" strokeWidth="5" strokeLinecap="round" />
              <text x="620" y="260" fill="#0284c7" fontSize="12" fontWeight="bold">Caño Maizaro / Caño Grande</text>

              {/* Río Guayuriba (Sur) */}
              <path d="M 70,350 Q 300,340 500,370 T 780,390" fill="none" stroke="#0284c7" strokeWidth="7" strokeLinecap="round" />
              <text x="610" y="380" fill="#0369a1" fontSize="13" fontWeight="bold">Río Guayuriba</text>

              {/* Estaciones Interactivas */}
              {zonas.map((z) => {
                // Coordenadas relativas en el lienzo vectorial
                const pos = z.zonaId === 1 ? { x: 300, y: 115 } : z.zonaId === 2 ? { x: 340, y: 355 } : { x: 390, y: 235 }
                const colorHex = COLORES_HEX[z.color] || COLORES_HEX['sin-datos']
                const esPeligro = z.color === 'rojo'

                return (
                  <g
                    key={z.zonaId}
                    className="estacion-svg-nodo"
                    onClick={() => setZonaSeleccionada(z)}
                    style={{ cursor: 'pointer' }}
                  >
                    {/* Aro exterior */}
                    <circle
                      cx={pos.x}
                      cy={pos.y}
                      r={esPeligro ? 18 : 13}
                      fill={colorHex}
                      opacity={esPeligro ? 0.35 : 0.25}
                    />
                    {/* Punto central */}
                    <circle cx={pos.x} cy={pos.y} r="8" fill={colorHex} stroke="#fff" strokeWidth="2.5" />
                    {/* Etiqueta */}
                    <text
                      x={pos.x + 15}
                      y={pos.y + 4}
                      fill="#0f172a"
                      fontSize="12"
                      fontWeight="bold"
                    >
                      {z.zona}
                    </text>
                  </g>
                )
              })}
            </svg>
          </div>

          {zonaSeleccionada && (
            <div className="detalle-nodo-vectorial">
              <div className="detalle-nodo-cabecera">
                <span className={`badge badge--${zonaSeleccionada.color}`}>{zonaSeleccionada.etiqueta}</span>
                <h4>{zonaSeleccionada.zona}</h4>
                <button type="button" className="btn-cerrar-mini" onClick={() => setZonaSeleccionada(null)}>✕</button>
              </div>
              <p><strong>Cuerpo de agua:</strong> {zonaSeleccionada.cuerpoAgua}</p>
              <p><strong>Lluvia acumulada:</strong> {milimetros(zonaSeleccionada.precipitacionMm)}</p>
              <p><strong>Comunidades:</strong> {zonaSeleccionada.comunidades}</p>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
