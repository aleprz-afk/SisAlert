package co.edu.unillanos.sisalert.alertas.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Seguridad del microservicio.
 *
 * Todas las rutas /api exigen un JWT válido emitido por la API de identidad: misma clave
 * HS256, emisor, audiencia y sin vencer. Las operaciones que cambian datos exigen el rol Admin:
 *
 *   GET  /api/zonas, /api/alertas, /api/lecturas/zonas/{id}/historico  -> cualquier usuario con sesión
 *   POST /api/lecturas/sincronizar                                     -> Admin
 *   POST, PUT, DELETE /api/zonas                                       -> Admin
 *   GET  /api/simulacion/{escenario} (simulación sin guardar)          -> Admin
 */
@Configuration
@EnableWebSecurity
public class SeguridadConfig {

    static final String ROL_ADMIN = "Admin";

    private final ObjectMapper mapper = new ObjectMapper();

    @Bean
    SecurityFilterChain filtroSeguridad(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint noAutenticado = (req, res, ex) -> escribirError(res, HttpStatus.UNAUTHORIZED,
                "NO_AUTENTICADO", "Debe iniciar sesión (token ausente, inválido o vencido).");

        http
                // API sin estado con el token en un encabezado: no hay cookie de sesión que proteger con CSRF.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/lecturas/sincronizar").hasRole(ROL_ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/zonas").hasRole(ROL_ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/zonas/**").hasRole(ROL_ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/api/zonas/**").hasRole(ROL_ADMIN)
                        .requestMatchers("/api/simulacion", "/api/simulacion/**").hasRole(ROL_ADMIN)
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new ConversorTokenIdentidad()))
                        .authenticationEntryPoint(noAutenticado))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(noAutenticado)
                        .accessDeniedHandler((req, res, ex) -> escribirError(res, HttpStatus.FORBIDDEN,
                                "SIN_PERMISO", "Esta acción requiere el rol de administrador.")));
        return http.build();
    }

    /**
     * Verifica la firma HS256 con la clave compartida, el vencimiento, el emisor y la
     * audiencia, tal como los emite la API de identidad.
     */
    @Bean
    JwtDecoder jwtDecoder(
            @Value("${alertas.jwt.clave}") String clave,
            @Value("${alertas.jwt.emisor}") String emisor,
            @Value("${alertas.jwt.audiencia}") String audiencia) {
        if (clave.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("La clave JWT (Jwt__Key) debe tener al menos 32 caracteres.");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(clave.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> validarAudiencia = token ->
                token.getAudience() != null && token.getAudience().contains(audiencia)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "Audiencia del token no válida", null));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(emisor), validarAudiencia));
        return decoder;
    }

    private void escribirError(HttpServletResponse res, HttpStatus estado, String codigo, String mensaje)
            throws IOException {
        res.setStatus(estado.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(res.getOutputStream(), Map.of("error", codigo, "mensaje", mensaje));
    }
}
