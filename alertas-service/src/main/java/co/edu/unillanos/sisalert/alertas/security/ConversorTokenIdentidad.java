package co.edu.unillanos.sisalert.alertas.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Traduce el JWT de la API de identidad (.NET) a la autenticación de Spring.
 *
 * Roles: .NET los escribe en el claim "role" (texto si es uno, lista si son varios) o con
 * el nombre largo de ClaimTypes.Role. Cada rol se convierte en "ROLE_nombre".
 * Nombre del usuario: "unique_name", con "sub" como respaldo (se usa en los logs).
 */
public class ConversorTokenIdentidad implements Converter<Jwt, AbstractAuthenticationToken> {

    static final List<String> CLAIMS_ROL = List.of(
            "role", "roles", "http://schemas.microsoft.com/ws/2008/06/identity/claims/role");

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> autoridades = new LinkedHashSet<>();
        for (String claim : CLAIMS_ROL) {
            Object valor = jwt.getClaim(claim);
            if (valor instanceof String rol && !rol.isBlank()) {
                autoridades.add(new SimpleGrantedAuthority("ROLE_" + rol));
            } else if (valor instanceof Collection<?> roles) {
                roles.forEach(r -> autoridades.add(new SimpleGrantedAuthority("ROLE_" + r)));
            }
        }
        String nombre = jwt.getClaimAsString("unique_name");
        return new JwtAuthenticationToken(jwt, autoridades, nombre != null ? nombre : jwt.getSubject());
    }
}
