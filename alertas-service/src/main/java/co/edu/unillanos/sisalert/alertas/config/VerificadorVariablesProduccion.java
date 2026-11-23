package co.edu.unillanos.sisalert.alertas.config;

import java.util.List;
import java.util.Locale;

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
            "DB_URL", "DB_USER", "DB_PASSWORD", "ALERTAS_CORS_ORIGENES", "Jwt__Key");

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

        // La base en la nube viaja por Internet: sin SSL las credenciales y los datos irían
        // en texto plano. Solo se permite sin SSL si se pide de forma explícita (por ejemplo,
        // un Docker Compose local donde la base está en la misma red privada).
        String url = environment.getProperty("DB_URL", "").toLowerCase(Locale.ROOT);
        boolean conSsl = url.contains("sslmode=require") || url.contains("sslmode=verify-ca")
                || url.contains("sslmode=verify-full");
        boolean sinSslPermitido = Boolean.parseBoolean(environment.getProperty("DB_SSL_OPCIONAL", "false"));
        if (!conSsl && !sinSslPermitido) {
            throw new IllegalStateException(
                    "Perfil prod: DB_URL debe usar SSL (agregar ?sslmode=require). "
                            + "Solo para una base en red privada se puede definir DB_SSL_OPCIONAL=true.");
        }
    }

    /** Después de que Spring cargue los archivos de configuración y los perfiles activos. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
