package co.edu.unillanos.sisalert.alertas.config;

import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * En el perfil "prod" verifica, antes de crear cualquier componente, que estén definidas
 * las variables de entorno obligatorias. Si falta alguna, el servicio no arranca y el
 * mensaje dice exactamente cuáles faltan (en lugar de un error confuso del pool de conexiones).
 *
 * Se registra en META-INF/spring.factories.
 */
public class VerificadorVariablesProduccion implements EnvironmentPostProcessor, Ordered {

    static final List<String> OBLIGATORIAS = List.of(
            "DB_URL", "DB_USER", "DB_PASSWORD", "ALERTAS_CORS_ORIGENES");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        List<String> faltantes = OBLIGATORIAS.stream()
                .filter(nombre -> {
                    String valor = environment.getProperty(nombre);
                    return valor == null || valor.isBlank();
                })
                .toList();
        if (!faltantes.isEmpty()) {
            throw new IllegalStateException(
                    "Perfil prod: faltan variables de entorno obligatorias: " + String.join(", ", faltantes));
        }
    }

    /** Después de que Spring cargue los archivos de configuración y los perfiles activos. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
