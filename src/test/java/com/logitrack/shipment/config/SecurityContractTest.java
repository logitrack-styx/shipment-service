package com.logitrack.shipment.config;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.crypto.spec.SecretKeySpec;

import com.logitrack.shipment.controller.EnvioController;
import com.logitrack.shipment.service.interfaces.EnvioService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica el filtro HTTP y el decodificador JWT reales con claves RSA efímeras.
 *
 * <p>No utiliza {@code jwt()} de Spring Security Test ni reemplaza JwtDecoder:
 * las firmas, los claims y las respuestas 401/403 atraviesan la configuración
 * de producción. No necesita security-service ni material criptográfico real.</p>
 */
@WebMvcTest(EnvioController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class SecurityContractTest {

    private static final KeyPair KEYS = generarParRsa();
    private static final Path PUBLIC_KEY = guardarClavePublica();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EnvioService envioService;

    /**
     * Configura únicamente la clave pública generada para esta suite.
     *
     * @param registry registro de propiedades del contexto de prueba
     */
    @DynamicPropertySource
    static void configurarClave(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.public-key-location", () -> PUBLIC_KEY.toUri().toString());
    }

    /** Comprueba que un ADMIN con firma y claims válidos puede listar envíos. */
    @Test
    void permiteAdminValido() throws Exception {
        org.mockito.Mockito.when(envioService.listar(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(Jwt.class), org.mockito.ArgumentMatchers.eq(0),
                org.mockito.ArgumentMatchers.eq(20))).thenReturn(org.springframework.data.domain.Page.empty());
        mockMvc.perform(get("/envios").header("Authorization", "Bearer " + token(b -> {})))
                .andExpect(status().isOk());
    }

    /** Comprueba que un conflicto optimista se publica como 409, nunca como 500. */
    @Test
    void conflictoOptimistaDevuelve409Comun() throws Exception {
        org.mockito.Mockito.when(envioService.listar(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(org.springframework.security.oauth2.jwt.Jwt.class),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(20)))
                .thenThrow(new org.springframework.dao.OptimisticLockingFailureException("interno"));
        mockMvc.perform(get("/envios").header("Authorization", "Bearer " + token(b -> {})))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.*", hasSize(4)));
    }

    /** Comprueba el contrato JSON de autenticación cuando falta el token. */
    @Test
    void sinTokenDevuelve401Comun() throws Exception {
        mockMvc.perform(get("/envios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.mensaje").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.*", hasSize(4)));
    }

    /** Comprueba que estar autenticado como CLIENTE no permite listar todos. */
    @Test
    void clienteDevuelve403Comun() throws Exception {
        mockMvc.perform(get("/envios").header("Authorization", "Bearer "
                        + token(b -> b.claim("roles", List.of("CLIENTE")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.mensaje").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.*", hasSize(4)));
    }

    /** Rechaza un token cuyo vencimiento supera la tolerancia del validador. */
    @Test
    void rechazaTokenExpirado() throws Exception {
        rechazar(token(b -> b.issuedAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600))));
    }

    /** Rechaza un emisor diferente al acordado entre servicios. */
    @Test
    void rechazaIssuerIncorrecto() throws Exception {
        rechazar(token(b -> b.issuer("otro-emisor")));
    }

    /** Rechaza sujetos que no representan un UUID de usuario. */
    @Test
    void rechazaSubjectInvalido() throws Exception {
        rechazar(token(b -> b.subject("usuario-sin-uuid")));
    }

    /** Rechaza UUID abreviados aceptados por UUID.fromString pero no canónicos. */
    @Test
    void rechazaSubjectNoCanonico() throws Exception {
        rechazar(token(b -> b.subject("1-1-1-1-1")));
    }

    /** Rechaza listas con más de un rol aunque los roles sean conocidos. */
    @Test
    void rechazaMultiplesRoles() throws Exception {
        rechazar(token(b -> b.claim("roles", List.of("ADMIN", "CLIENTE"))));
    }

    /** Rechaza roles ajenos al contrato. */
    @Test
    void rechazaRolDesconocido() throws Exception {
        rechazar(token(b -> b.claim("roles", List.of("SUPERADMIN"))));
    }

    /** Rechaza el formato string en lugar del array contractual de roles. */
    @Test
    void rechazaRolesComoString() throws Exception {
        rechazar(token(b -> b.claim("roles", "ADMIN")));
    }

    /** Rechaza tokens que omiten la expiración obligatoria. */
    @Test
    void rechazaTokenSinExpiracion() throws Exception {
        rechazar(token(b -> b.claims(c -> c.remove("exp"))));
    }

    /** Rechaza una firma RSA válida producida con una clave no confiable. */
    @Test
    void rechazaFirmaDeOtraClave() throws Exception {
        rechazar(firmarRsa(generarParRsa(), claims(b -> {})));
    }

    /** Rechaza HS256 aunque el token posea todos los claims requeridos. */
    @Test
    void rechazaAlgoritmoHs256() throws Exception {
        var secret = new SecretKeySpec(new byte[32], "HmacSHA256");
        var jwk = new OctetSequenceKey.Builder(secret).build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims(b -> {}))).getTokenValue();
        rechazar(token);
    }

    /**
     * Comprueba la respuesta HTTP real para un token inválido.
     *
     * @param token token firmado que incumple el contrato
     * @throws Exception si falla la petición simulada
     */
    private void rechazar(String token) throws Exception {
        mockMvc.perform(get("/envios").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    /**
     * Genera un token RSA con una modificación controlada de sus claims.
     *
     * @param modificar modificación a aplicar al contrato válido
     * @return token compacto firmado
     */
    private static String token(Consumer<JwtClaimsSet.Builder> modificar) {
        return firmarRsa(KEYS, claims(modificar));
    }

    /**
     * Construye claims válidos y aplica la variación de la prueba.
     *
     * @param modificar modificación controlada
     * @return claims resultantes
     */
    private static JwtClaimsSet claims(Consumer<JwtClaimsSet.Builder> modificar) {
        Instant now = Instant.now();
        var builder = JwtClaimsSet.builder().issuer("logitrack-security")
                .subject(UUID.randomUUID().toString()).issuedAt(now)
                .expiresAt(now.plusSeconds(3600)).claim("roles", List.of("ADMIN"));
        modificar.accept(builder);
        return builder.build();
    }

    /**
     * Firma los claims con RS256 sin utilizar claves de integración.
     *
     * @param pair par RSA de prueba
     * @param claims claims a firmar
     * @return token compacto firmado
     */
    private static String firmarRsa(KeyPair pair, JwtClaimsSet claims) {
        var jwk = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate()).build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    /**
     * Genera un par RSA efímero exclusivamente para pruebas.
     *
     * @return par de claves de 2048 bits
     */
    private static KeyPair generarParRsa() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo generar RSA de prueba", ex);
        }
    }

    /**
     * Escribe solo la clave pública temporal requerida por el decoder real.
     *
     * @return ubicación del PEM temporal
     */
    private static Path guardarClavePublica() {
        try {
            Path file = Files.createTempFile("shipment-test-public-", ".pem");
            String body = Base64.getMimeEncoder(64, new byte[]{'\n'})
                    .encodeToString(KEYS.getPublic().getEncoded());
            Files.writeString(file, "-----BEGIN PUBLIC KEY-----\n" + body
                    + "\n-----END PUBLIC KEY-----\n", StandardCharsets.US_ASCII);
            file.toFile().deleteOnExit();
            return file;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("No se pudo preparar el PEM de prueba", ex);
        }
    }
}
