package com.logitrack.shipment.config;

import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.converter.RsaKeyConverters;

/**
 * Configuración central de seguridad para la aplicación LogiTrack Shipment.
 * Gestiona la autenticación y autorización mediante recursos OAuth2 (JWT),
 * define las reglas de acceso a los endpoints HTTP y configura los manejadores
 * de errores para accesos no autorizados o prohibidos.
 *
 * @author dominox45
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Configura la cadena de filtros de seguridad HTTP (SecurityFilterChain),
     * definiendo políticas CSRF, reglas de autorización por rol y endpoints públicos,
     * así como la integración del servidor de recursos OAuth2 y gestión de excepciones.
     *
     * @param http                     instancia de {@link HttpSecurity} a configurar.
     * @param jwtAuthenticationConverter conversor de autenticación JWT para los roles.
     * @param authenticationEntryPoint manejador para errores de autenticación (401).
     * @param accessDeniedHandler      manejador para errores de autorización (403).
     * @return la cadena de filtros de seguridad construida.
     * @throws Exception si ocurre un error durante la configuración.
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler) throws Exception {

        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(
                                org.springframework.http.HttpMethod.POST,
                                "/envios"
                        ).hasAnyRole("CLIENTE", "ADMIN")
                        .requestMatchers(
                                org.springframework.http.HttpMethod.GET,
                                "/envios"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                org.springframework.http.HttpMethod.PUT,
                                "/envios/*/repartidor"
                        ).hasRole("ADMIN")
                        .requestMatchers(
                                org.springframework.http.HttpMethod.PATCH,
                                "/envios/*/cancelacion"
                        ).hasAnyRole("ADMIN", "CLIENTE")
                        .requestMatchers(
                                org.springframework.http.HttpMethod.PATCH,
                                "/envios/*/estado"
                        ).hasAnyRole("ADMIN", "REPARTIDOR")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/envios/mine").hasRole("CLIENTE")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/envios/assigned-to-me").hasRole("REPARTIDOR")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/envios/*/assignment-requests").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/envios/*/assignment-requests/*").hasRole("ADMIN")
                        .requestMatchers("/envios/**")
                        .hasAnyRole("ADMIN", "CLIENTE", "REPARTIDOR")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));

        return http.build();
    }

    /**
     * Configura el decodificador de tokens JWT utilizando una clave pública RSA
     * y validadores personalizados para asegurar que los claims (como el sujeto y roles)
     * cumplan con el contrato de negocio de LogiTrack.
     *
     * @param resourceLoader cargador de recursos de Spring.
     * @param keyLocation    ubicación de la clave pública en formato PEM.
     * @param issuer         emisor esperado del token JWT.
     * @return una instancia configurada de {@link JwtDecoder}.
     * @throws Exception si ocurre un error al leer la clave o inicializar el decodificador.
     */
    @Bean
    JwtDecoder jwtDecoder(
            ResourceLoader resourceLoader,
            @Value("${security.jwt.public-key-location:classpath:keys/jwt-test-public.pem}")
            String keyLocation,
            @Value("${security.jwt.issuer:logitrack-security}")
            String issuer) throws Exception {

        Resource resource = resourceLoader.getResource(keyLocation);

        RSAPublicKey publicKey;
        try (InputStream input = resource.getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(input);
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(publicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> contractClaims = jwt -> {
            Object rolesClaim = jwt.getClaims().get("roles");

            boolean validRole =
                    rolesClaim instanceof Collection<?> roles
                            && roles.size() == 1
                            && roles.iterator().next() instanceof String role
                            && List.of("ADMIN", "CLIENTE", "REPARTIDOR")
                            .contains(role);

            boolean validSubject;
            try {
                String subject = jwt.getSubject();
                validSubject = UUID.fromString(subject).toString().equals(subject);
            } catch (RuntimeException ex) {
                validSubject = false;
            }

            if (validRole && validSubject && jwt.getExpiresAt() != null) {
                return OAuth2TokenValidatorResult.success();
            }

            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error(
                            "invalid_token",
                            "Los claims sub/roles no cumplen el contrato LogiTrack",
                            null));
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer),
                        contractClaims));

        return decoder;
    }

    /**
     * Configura el conversor para extraer las autoridades (roles) desde el token JWT,
     * mapeándolas al formato requerido por Spring Security con el prefijo "ROLE_".
     *
     * @return una instancia configurada de {@link JwtAuthenticationConverter}.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object claim = jwt.getClaims().get("roles");

            if (!(claim instanceof Collection<?> roles)) {
                return List.of();
            }

            return roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(role -> List.of(
                            "ADMIN",
                            "CLIENTE",
                            "REPARTIDOR"
                    ).contains(role))
                    .map(role ->
                            (org.springframework.security.core.GrantedAuthority)
                                    new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
        });

        return converter;
    }

    /**
     * Define el punto de entrada de autenticación para manejar solicitudes no autenticadas,
     * devolviendo una respuesta de error estructurada en formato JSON con código HTTP 401.
     *
     * @param mapper objeto {@link ObjectMapper} para serializar la respuesta JSON.
     * @return una implementación de {@link AuthenticationEntryPoint}.
     */
    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(
            ObjectMapper mapper) {

        return (request, response, exception) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            mapper.writeValue(
                    response.getOutputStream(),
                    Map.of(
                            "status", 401,
                            "error", "Unauthorized",
                            "mensaje", "Token ausente, inválido o expirado",
                            "timestamp",
                            OffsetDateTime.now(ZoneOffset.UTC).toString()
                    ));
        };
    }

    /**
     * Define el manejador de acceso denegado para solicitudes que carecen de permisos suficientes,
     * devolviendo una respuesta de error estructurada en formato JSON con código HTTP 403.
     *
     * @param mapper objeto {@link ObjectMapper} para serializar la respuesta JSON.
     * @return una implementación de {@link AccessDeniedHandler}.
     */
    @Bean
    AccessDeniedHandler accessDeniedHandler(ObjectMapper mapper) {
        return (request, response, exception) -> {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            mapper.writeValue(
                    response.getOutputStream(),
                    Map.of(
                            "status", 403,
                            "error", "Forbidden",
                            "mensaje",
                            "No tiene permisos para realizar esta operación",
                            "timestamp",
                            OffsetDateTime.now(ZoneOffset.UTC).toString()
                    ));
        };
    }
}
