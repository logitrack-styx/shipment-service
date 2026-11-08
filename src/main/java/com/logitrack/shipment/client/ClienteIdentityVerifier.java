package com.logitrack.shipment.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

/**
 * Comprueba el destinatario de altas administrativas contra la autoridad de identidad.
 * El token ADMIN se reenvía internamente, nunca se persiste. No usa relaciones JPA remotas.
 * @author dominox45
 */
@Component
public class ClienteIdentityVerifier {
    private final RestClient client;

    /**
     * @param builder cliente HTTP de Spring
     * @param url dirección interna de Security
     */
    public ClienteIdentityVerifier(RestClient.Builder builder,
            @Value("${logitrack.clients.security.base-url:http://security-service:8081}") String url) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        client = builder.baseUrl(url).requestFactory(factory).build();
    }

    /**
     * @param id usuario destinatario del envío administrativo
     * @throws IllegalArgumentException si no existe o no es CLIENTE habilitado
     */
    public void verificar(UUID id) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            throw new org.springframework.security.access.AccessDeniedException("Se requiere identidad ADMIN");
        }
        try {
            var user = client.get().uri("/users/{id}", id)
                    .headers(h -> h.setBearerAuth(jwt.getToken().getTokenValue()))
                    .retrieve().body(Usuario.class);
            if (user == null || !id.equals(user.id()) || !"CLIENTE".equals(user.role()) || !user.eneable()) {
                throw new IllegalArgumentException("El destinatario debe ser un CLIENTE habilitado");
            }
        } catch (HttpClientErrorException.NotFound ex) {
            throw new com.logitrack.shipment.exception.RecursoNoEncontradoException("Cliente inexistente");
        } catch (RestClientException ex) {
            throw new com.logitrack.shipment.exception.IdentityIntegrationException("No se pudo validar el cliente");
        }
    }

    /** Proyección remota mínima; no contiene credenciales. @author dominox45 */
    record Usuario(UUID id, String role, boolean eneable) { }
}
