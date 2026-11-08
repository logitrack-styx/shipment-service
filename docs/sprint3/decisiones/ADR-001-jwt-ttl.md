# ADR 001 — RS256, identidad y TTL

Decisión: conservar RS256 y 3600 segundos como valor predeterminado para mantener el contrato de login y la demo. `JWT_ACCESS_TOKEN_TTL` / `security.jwt.access-token-ttl` permite cambiar segundos sin recompilar; se rechaza cero o negativo al arrancar. No se incorpora refresh token.

Security mantiene la clave privada; Gateway y APIs verifican con la pública, issuer `logitrack-security`, exp, UUID canónico y una lista de exactamente un rol ADMIN, CLIENTE o REPARTIDOR. Un token inválido produce 401; un token válido sin permiso produce 403. La tolerancia de reloj predeterminada de Spring es 60 segundos: los negativos de expiración usan más de ese margen. Las cuentas deshabilitadas no pueden obtener tokens nuevos; la revocación inmediata de tokens ya emitidos no forma parte del contrato actual.

«SCL»: término no confirmado. No hay grabación ni transcripción que permita equipararlo a SSL/TLS, scope u otro algoritmo. RS256 es firma RSA con SHA-256; no cifra el transporte ni reemplaza TLS.

Fuente: https://docs.spring.io/spring-security/reference/6.5/reactive/oauth2/resource-server/jwt.html y RFC 7518. La verificación real se registra por suite y E2E, no se deduce de este ADR.
