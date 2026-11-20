package co.edu.unillanos.sisalert.alertas.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class VerificadorVariablesProduccionTest {

    private final VerificadorVariablesProduccion verificador = new VerificadorVariablesProduccion();

    @Test
    void enDevNoExigeNada() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        assertDoesNotThrow(() -> verificador.postProcessEnvironment(env, null));
    }

    @Test
    void enProdNombraLasVariablesQueFaltan() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("DB_URL", "jdbc:postgresql://x/sisalert")
                .withProperty("DB_USER", "u");
        env.setActiveProfiles("prod");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> verificador.postProcessEnvironment(env, null));
        assertTrue(error.getMessage().contains("DB_PASSWORD"));
        assertTrue(error.getMessage().contains("ALERTAS_CORS_ORIGENES"));
    }

    private static MockEnvironment prodCompleto(String dbUrl) {
        MockEnvironment env = new MockEnvironment();
        VerificadorVariablesProduccion.OBLIGATORIAS.forEach(nombre -> env.setProperty(nombre, "valor"));
        env.setProperty("DB_URL", dbUrl);
        env.setActiveProfiles("prod");
        return env;
    }

    @Test
    void enProdConTodasLasVariablesYSslArranca() {
        MockEnvironment env = prodCompleto("jdbc:postgresql://srv.postgres.database.azure.com:5432/sisalert?sslmode=require");
        assertDoesNotThrow(() -> verificador.postProcessEnvironment(env, null));
    }

    @Test
    void enProdRechazaUnaBaseSinSsl() {
        MockEnvironment env = prodCompleto("jdbc:postgresql://srv:5432/sisalert");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> verificador.postProcessEnvironment(env, null));
        assertTrue(error.getMessage().contains("sslmode=require"));
    }

    @Test
    void enProdPermiteSinSslSoloSiSePideExplicitamente() {
        MockEnvironment env = prodCompleto("jdbc:postgresql://db:5432/sisalert");
        env.setProperty("DB_SSL_OPCIONAL", "true");
        assertDoesNotThrow(() -> verificador.postProcessEnvironment(env, null));
    }
}
