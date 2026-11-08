# Iteración 01 — inventario y JWT de Gateway

Fecha: 2026-11-08, America/Buenos_Aires.

Estado global: PENDIENTE. No se cerraron issues ni se publicaron PRs.

## Cambios

- Inventario de los siete repositorios, fetch y comparación de ramas/remotos; 15 issues abiertos, 11 S3 cerrados pendientes de revalidación.
- Matriz en `auditoria-inicial.md`, con criterios extraídos de GitHub y evidencias JSON de issues, comentarios y PRs.
- Rama Gateway `fix/s3-jwt-validation`: decoder RS256 ahora aplica issuer y timestamps mediante `JwtValidators.createDefaultWithIssuer`, y exige expiración, UUID canónico y lista no vacía de roles conocidos. Conserva cambios locales previos del usuario.
- Prueba `JwtDecoderContractTest` con RSA efímera: acepta token válido y rechaza issuer ajeno, expirado, UUID inválido, roles string/vacíos/desconocidos/no textuales y firma de otra clave.
- Rama Shipment `docs/s3-auditoria-cierre`: auditoría y evidencias locales.

## Verificación ejecutada

Comando por servicio: `mvnw.cmd -B -ntp test`, con `JAVA_HOME=C:/Program Files/Java/jdk-25.0.4` y acceso autorizado a caché Maven.

| Suite | Resultado observado |
|---|---|
| Security | BUILD SUCCESS; 3 tests, sin errores |
| Courier | BUILD SUCCESS; 19 tests, sin errores |
| Gateway baseline | BUILD SUCCESS; 1 test de contexto |
| Gateway con corrección JWT | BUILD SUCCESS; 2 tests, sin errores |
| Shipment | BUILD FAILURE; 80 tests reportados, 1 error: EnvioRepositoryTest no encuentra Docker válido |
| Eureka | BUILD SUCCESS; revisar log para detalle |
| Config | BUILD SUCCESS, sin tests |

`docker compose config --quiet`: exit 0. Solo valida configuración; no acredita salud, registro Eureka ni imágenes propias. `git diff --check` de Gateway y Shipment no reportó errores.

## Riesgos y pendientes

S3-12 continúa PENDIENTE: faltan negativos HTTP 401/403, algoritmo no permitido, claims ausentes y homogeneidad downstream. El test nuevo acredita el decoder, no el recorrido completo por Gateway.

Docker inicialmente sin motor disponible; se intentó `docker desktop start` para habilitar las verificaciones. No se borraron datos ni volúmenes. No se ejecutaron los escenarios E2E ni la recuperación Courier.

Faltan fuentes normativas originales, revisión profunda de PRs y comprobación de evidencias enlazadas. Los comentarios consultados de S3 resultaron vacíos hasta el relevamiento observado; CLOSED no prueba cumplimiento.

La recuperación de Cliente, TTL configurable, deny-by-default, Swagger, paginación, ADR PUT/POST y colección Apidog aún están PENDIENTES. No hay commit/PR nuevo que acreditar en esta iteración.

Referencia oficial consultada para issuer/timestamps y decoder reactivo: https://docs.spring.io/spring-security/reference/reactive/oauth2/resource-server/jwt.html . Debe mantenerse la compatibilidad con las dependencias efectivas del proyecto; la compilación y tests del Gateway fueron ejecutados.

Docker Desktop inició correctamente. docker info confirmó motor 29.8.0. Se repitió Shipment con Docker y se inició Compose: resultados aún pendientes; no equivalen a VALIDADO.


## Resultado final del relevamiento ejecutado

- Docker Desktop disponible, motor 29.8.0. Compose existente: nueve contenedores saludables tras `docker compose up -d --wait --wait-timeout 180` (exit 0). No se borraron volúmenes.
- Eureka: API-GATEWAY, SECURITY-SERVICE, COURIER-SERVICE y SHIPMENT-SERVICE UP; evidencia eureka-registry.json.
- Shipment repetido con Docker: BUILD SUCCESS, 86 tests, cero fallos, errores y omitidos. El error anterior queda registrado como limitación inicial resuelta.
- Config: BUILD SUCCESS, No tests to run. No equivale a suite funcional.
- Swagger interno sin autenticación: Security y Shipment devuelven HTTP 200 en /v3/api-docs y /swagger-ui/index.html; Courier devuelve 401 en ambas rutas. Falta validar contenido OpenAPI y UI visual: no se marca Swagger VALIDADO.
- Arranque y registro corresponden a imágenes existentes 0.3.0, no al cambio JWT local. No se verificaron procedencia SHA/digest ni publicación de las imágenes.
- No ejecutados: recuperación Courier, 7+20 escenarios Apidog/E2E, negativos HTTP del Gateway y migraciones Cliente. No hay cierres, commits ni PR nuevos.
- Fase 0 tiene inventario y ejecución de baseline, pero sigue PENDIENTE de contrastar fuentes normativas ausentes y revisar evidencia individual de PRs. Los demás frentes siguen PENDIENTES, salvo corrección de decoder verificada al nivel indicado.
