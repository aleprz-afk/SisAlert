package co.edu.unillanos.sisalert.alertas.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import co.edu.unillanos.sisalert.alertas.service.AlertaService;
import co.edu.unillanos.sisalert.alertas.service.SimulacionService;
import co.edu.unillanos.sisalert.alertas.service.SincronizacionService;
import co.edu.unillanos.sisalert.alertas.service.ZonaService;

/**
 * Comprueba las reglas de acceso con tokens reales firmados igual que los emite la API
 * de identidad (HS256, mismo emisor y audiencia, rol en el claim "role").
 */
@WebMvcTest
@Import(SeguridadConfig.class)
class SeguridadConfigTest {

    // Mismos valores que src/test/resources/application.properties
    private static final String CLAVE = "clave-de-pruebas-unitarias-con-mas-de-32-caracteres";
    private static final String EMISOR = "MiniIdentityApi";
    private static final String AUDIENCIA = "MiniIdentityApiUsers";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ZonaService zonaService;
    @MockitoBean
    private AlertaService alertaService;
    @MockitoBean
    private SincronizacionService sincronizacionService;
    @MockitoBean
    private SimulacionService simulacionService;

    private static String token(String clave, String emisor, Instant vence, Object rol) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("123")
                .claim("unique_name", "usuario")
                .issuer(emisor)
                .audience(AUDIENCIA)
                .expirationTime(Date.from(vence));
        if (rol != null) {
            claims.claim("role", rol);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(clave.getBytes(StandardCharsets.UTF_8)));
        return "Bearer " + jwt.serialize();
    }

    private static String tokenValido(Object rol) throws Exception {
        return token(CLAVE, EMISOR, Instant.now().plusSeconds(3600), rol);
    }

    @Test
    void sinTokenResponde401ConFormatoDelProyecto() throws Exception {
        mvc.perform(get("/api/alertas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"));
    }

    @Test
    void tokenFirmadoConOtraClaveEsRechazado() throws Exception {
        String falso = token("otra-clave-cualquiera-de-mas-de-32-caracteres!!", EMISOR,
                Instant.now().plusSeconds(3600), "Admin");
        mvc.perform(get("/api/alertas").header("Authorization", falso))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenVencidoEsRechazado() throws Exception {
        String vencido = token(CLAVE, EMISOR, Instant.now().minusSeconds(3600), "Admin");
        mvc.perform(get("/api/alertas").header("Authorization", vencido))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeOtroEmisorEsRechazado() throws Exception {
        String otroEmisor = token(CLAVE, "OtraApi", Instant.now().plusSeconds(3600), "Admin");
        mvc.perform(get("/api/alertas").header("Authorization", otroEmisor))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioSinRolPuedeConsultar() throws Exception {
        when(alertaService.listarVigentes()).thenReturn(List.of());
        mvc.perform(get("/api/alertas").header("Authorization", tokenValido(null)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/zonas").header("Authorization", tokenValido(null)))
                .andExpect(status().isOk());
    }

    @Test
    void usuarioSinRolNoPuedeSincronizarNiBorrarZonas() throws Exception {
        mvc.perform(post("/api/lecturas/sincronizar").header("Authorization", tokenValido(null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("SIN_PERMISO"));
        mvc.perform(delete("/api/zonas/1").header("Authorization", tokenValido(null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminPuedeSincronizarYBorrarZonas() throws Exception {
        when(sincronizacionService.sincronizar()).thenReturn(List.of());
        mvc.perform(post("/api/lecturas/sincronizar").header("Authorization", tokenValido("Admin")))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/zonas/1").header("Authorization", tokenValido("Admin")))
                .andExpect(status().isNoContent());
    }

    @Test
    void reconoceVariosRolesComoLista() throws Exception {
        when(sincronizacionService.sincronizar()).thenReturn(List.of());
        mvc.perform(post("/api/lecturas/sincronizar")
                        .header("Authorization", tokenValido(List.of("Operador", "Admin"))))
                .andExpect(status().isOk());
    }

    @Test
    void soloAdminPuedeVerSimulaciones() throws Exception {
        when(simulacionService.simular("tormenta")).thenReturn(List.of());
        mvc.perform(get("/api/simulacion/tormenta").header("Authorization", tokenValido(null)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/simulacion/tormenta").header("Authorization", tokenValido("Admin")))
                .andExpect(status().isOk());
    }
}
