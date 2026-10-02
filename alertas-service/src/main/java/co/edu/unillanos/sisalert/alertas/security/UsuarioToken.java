package co.edu.unillanos.sisalert.alertas.security;

import java.util.Base64;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Lee el nombre de usuario del payload del JWT. NO valida la firma:
 * en esta etapa el microservicio solo identifica quién llama.
 */
@Component
public class UsuarioToken {

    private static final List<String> CLAIMS = List.of(
            "unique_name", "name",
            "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name",
            "username", "email", "sub");

    private final ObjectMapper mapper = new ObjectMapper();

    public String extraer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "anonimo";
        }
        try {
            String[] partes = authorization.substring(7).split("\\.");
            JsonNode payload = mapper.readTree(Base64.getUrlDecoder().decode(partes[1]));
            for (String claim : CLAIMS) {
                JsonNode valor = payload.get(claim);
                if (valor != null && !valor.asText().isBlank()) {
                    return valor.asText();
                }
            }
        } catch (Exception ignorada) {
            // token mal formado: se trata como anónimo
        }
        return "anonimo";
    }
}