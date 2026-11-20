package co.edu.unillanos.sisalert.alertas.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unillanos.sisalert.alertas.client.ClienteEscenario;
import co.edu.unillanos.sisalert.alertas.client.ClientePrediccionIa;
import co.edu.unillanos.sisalert.alertas.client.PrediccionIa;
import co.edu.unillanos.sisalert.alertas.dto.AlertaDto;
import co.edu.unillanos.sisalert.alertas.dto.RiesgoIaDto;
import co.edu.unillanos.sisalert.alertas.exception.RecursoNoEncontradoException;
import co.edu.unillanos.sisalert.alertas.model.NivelAlerta;
import co.edu.unillanos.sisalert.alertas.model.Zona;
import co.edu.unillanos.sisalert.alertas.repository.ZonaRepository;
import co.edu.unillanos.sisalert.alertas.rules.ReglaDeUmbral;

/**
 * Simulación para administradores: muestra cómo respondería el sistema (semáforo y
 * predicción de IA) ante un escenario de lluvia, SIN guardar nada.
 *
 * Los datos reales no se tocan: el monitoreo automático sigue registrando Open-Meteo y los
 * demás usuarios siguen viendo GET /api/alertas. Así un administrador puede alternar en vivo
 * entre el estado actual y un escenario simulado, sin reiniciar ningún servicio.
 */
@Service
public class SimulacionService {

    private final ZonaRepository zonaRepository;
    private final ReglaDeUmbral regla;
    private final ClientePrediccionIa clienteIa;
    private final BoletinService boletinService;
    private final double umbralPrecaucion;
    private final double umbralPeligro;

    public SimulacionService(ZonaRepository zonaRepository,
                             ReglaDeUmbral regla,
                             ClientePrediccionIa clienteIa,
                             BoletinService boletinService,
                             @Value("${alertas.umbral.precaucion-mm}") double umbralPrecaucion,
                             @Value("${alertas.umbral.peligro-mm}") double umbralPeligro) {
        this.zonaRepository = zonaRepository;
        this.regla = regla;
        this.clienteIa = clienteIa;
        this.boletinService = boletinService;
        this.umbralPrecaucion = umbralPrecaucion;
        this.umbralPeligro = umbralPeligro;
    }

    /** Escenarios disponibles, en el orden en que se muestran. */
    public List<String> escenarios() {
        return ClienteEscenario.ESCENARIOS;
    }

    /** Mismo formato que GET /api/alertas, calculado al vuelo para el escenario pedido. */
    @Transactional(readOnly = true)
    public List<AlertaDto> simular(String escenario) {
        String nombre = escenario == null ? "" : escenario.trim().toLowerCase(Locale.ROOT);
        if (!ClienteEscenario.ESCENARIOS.contains(nombre)) {
            throw new RecursoNoEncontradoException(
                    "Escenario no encontrado: " + escenario + ". Disponibles: " + ClienteEscenario.ESCENARIOS);
        }
        ClienteEscenario fuente = new ClienteEscenario(nombre, umbralPrecaucion, umbralPeligro);
        LocalDateTime ahora = LocalDateTime.now();

        return zonaRepository.findAll(Sort.by("id")).stream()
                .map(zona -> simularZona(zona, fuente, nombre, ahora))
                .toList();
    }

    private AlertaDto simularZona(Zona zona, ClienteEscenario fuente, String escenario, LocalDateTime ahora) {
        double mm = fuente.precipitacionAcumuladaMm(zona.getCodigoEstacion());
        NivelAlerta nivel = regla.evaluar(mm);
        // La IA también se consulta en vivo con la serie del escenario (no se guarda).
        Optional<PrediccionIa> prediccion = clienteIa.habilitado()
                ? fuente.serieHoraria(zona.getCodigoEstacion()).flatMap(clienteIa::predecir)
                : Optional.empty();
        RiesgoIaDto riesgoIa = prediccion
                .map(p -> new RiesgoIaDto(p.riesgo(), p.probabilidad(), p.factores(), p.versionModelo(), ahora))
                .orElse(null);
        return new AlertaDto(zona.getId(), zona.getNombre(), zona.getCuerpoAgua(), zona.getComunidades(),
                nivel, regla.mensajePara(nivel), mm, ahora, riesgoIa,
                boletinService.simulado(zona, nivel, mm, prediccion.orElse(null), escenario));
    }
}
