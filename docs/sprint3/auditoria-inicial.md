# Auditoría inicial — cierre definitivo Sprint 3

Fecha: 2026-11-08 (America/Buenos_Aires). Estado: PENDIENTE. Ningún issue cerrado por esta auditoría.

## Línea base Git

| Repositorio | Rama | SHA | Divergencia HEAD...origin |
|---|---|---|---|
| security-service | develop | babf9cf3a3f8884339f6e8dc806d478ad98380a3 | 0	0 |
| courier-service | develop | 318a8e0c68f2adfe91a507576245db95b87ff654 | 0	0 |
| shipment-service | develop | 07acefbbb3184ba066b1a6aaf8c0b6e3a1eb545c | 0	0 |
| api-gateway | develop | 1595282967da3b305ee46d512fc1453f33c8e27a | 0	0 |
| eureka-server | develop | 79cec8d23e0b0f18246e78c248eaaa2b73ff44af | 0	0 |
| config-server | develop | 56e23f2dd41e870c1fac9dcabebf2240b321d71c | 0	0 |
| styx-config | main | 031654c0aa2f286414ff0cf796e2781ecaa1eb80 | 0	0 |

Fetch origin ejecutado para los siete repositorios sin modificar archivos. Gateway tenía modificación local previa en SecurityConfig.java (8 inserciones y 8 eliminaciones); debe preservarse. No se encontraron AGENTS.md en el árbol. La carpeta STYX no es repositorio Git.

## Evidencia y limitaciones

- GitHub: inventarios completos de issues y PRs en evidencias/*.json; 15 abiertos y 11 S3 cerrados. Los estados administrativos no acreditan aceptación. Comentarios y evidencias enlazadas aún requieren revisión.
- Compose global: ../../../docker-compose.yml desde esta carpeta; nueve servicios declarados. docker compose config --quiet terminó con código 0. No está versionado: STYX no tiene .git.
- docker version no pudo conectar al pipe dockerDesktopLinuxEngine: sin arranque integrado, MySQL, Eureka UP ni E2E verificables todavía.
- Java 25.0.4 instalado en C:/Program Files/Java/jdk-25.0.4. El java del PATH no respondió durante el primer intento; usar JAVA_HOME explícito. Maven wrapper existe en los seis servicios; Maven global no está en PATH.
- Primer test Gateway bloqueado por acceso a caché .m2; reintentado con acceso autorizado. Resultados de suites en evidencias/test-*.log (revisar finalización, no presumir éxito).
- No se encontraron consignas PDF, contrato S2, contrato S3 v1.2 ni matriz JSON en el árbol o entre los nombres de Downloads inspeccionados. No se confirma término SCL.
- Colección local logitrack-sprint3-apidog-collection.json: baseUrlGateway aparece tres veces; variables únicas: baseUrlGateway, adminUsername, adminPassword, tokenAdmin, tokenCliente, tokenRepartidor, clienteId, repartidorUserId, envioId, trackingCode. No se acredita equivalencia con el export de referencia ausente. No se imprimieron valores sensibles.

## Matriz de trabajo por issue

P0: seguridad o requisito transversal bloqueante. P1: contrato/operación. P2: defensa. Todos pendientes de aceptación; CLOSED en GitHub no equivale a VALIDADO.

| Issue | GitHub / trabajo | Prioridad / riesgo | Ruta de código a inspeccionar | Prueba de aceptación pendiente |
|---|---|---|---|---|
| [S3-14 config-server#8](https://github.com/logitrack-styx/config-server/issues/8) | OPEN / PENDIENTE | P1 / alto | api-gateway/src/main/java/com/logitrack/gateway/config/SecurityConfig.java | Preflight autorizado responde correctamente ; No hay `*` con credenciales ; No se duplica CORS innecesariamente en downstream ; Errores de seguridad estructurados y sin datos sensibles |
| [S3-11 config-server#7](https://github.com/logitrack-styx/config-server/issues/7) | OPEN / PENDIENTE | P1 / alto | styx-config/api-gateway.yaml | Requests desde :8066 alcanzan destino correcto sin puertos de negocio ; Paths base y subrutas funcionan sin 404 artificial ; No existen rutas arbitrarias automáticas ; `Authorization` llega intacta downstream |
| [S3-10 config-server#6](https://github.com/logitrack-styx/config-server/issues/6) | CLOSED / PENDIENTE | P1 / alto | api-gateway/src/main | Gateway arranca por :8066 y aparece UP en Eureka ; Lee configuración desde Config Server ; No mezcla servlet MVC en Gateway ni expone rutas automáticas por discovery locator ; Código versionado en repositorio propio cuando se cree |
| [S3-03 config-server#5](https://github.com/logitrack-styx/config-server/issues/5) | CLOSED / PENDIENTE | P1 / alto | eureka-server/src/main/resources/application.yaml | Arranca leyendo propiedades desde Config Server ; Dashboard responde en :8065 y healthcheck sano ; No se registra a sí mismo ; Proyecto y Dockerfile versionados en repositorio nuevo cuando se cree |
| [S3-20 courier-service#20](https://github.com/logitrack-styx/courier-service/issues/20) | OPEN / PENDIENTE | P1 / alto | courier-service/src/main; styx-config/courier-service.yaml | La llamada URL Courier→Security está identificada y tiene decisión escrita ; No se afirma desacoplamiento total si la URL sigue fija ; `ddl-auto:update` queda registrado como deuda o se corrige con migración comprobada |
| [S3-16 courier-service#19](https://github.com/logitrack-styx/courier-service/issues/19) | OPEN / PENDIENTE | P1 / alto | docker-compose.yml; styx-config/eureka-server.yaml | Nueve contenedores healthy en arranque limpio ; Cuatro clientes UP registrados en Eureka ; Recuperación tras `docker compose stop/start courier-service` verificada por petición ; Sin condiciones de carrera observadas en pruebas |
| [S3-15 courier-service#18](https://github.com/logitrack-styx/courier-service/issues/18) | OPEN / PENDIENTE | P1 / alto | docker-compose.yml; */Dockerfile | `docker compose up -d` levanta nueve servicios saludables ; Gateway única entrada externa de negocio ; DB y microservicios de dominio no publican puertos en host ; No se guardan `.env` ni llaves privadas |
| [S3-05 courier-service#17](https://github.com/logitrack-styx/courier-service/issues/17) | CLOSED / PENDIENTE | P1 / alto | courier-service/pom.xml | `COURIER-SERVICE` aparece UP ; Se mantiene GET `/couriers/{id}/disponibilidad` con UUID ; Pruebas de permisos y disponibilidad siguen pasando |
| [courier-service#6](https://github.com/logitrack-styx/courier-service/issues/6) | OPEN / PENDIENTE | P1 / alto | courier-service/src; docker-compose.yml | Configurar base de datos MySQL y propiedades de entorno para courier-service: tests de dominio, MySQL y flujo integrado según issue; evidencia aún pendiente |
| [courier-service#4](https://github.com/logitrack-styx/courier-service/issues/4) | OPEN / PENDIENTE | P1 / alto | courier-service/src; docker-compose.yml | Fase 4 - Validación end-to-end del entorno LogiTrack: tests de dominio, MySQL y flujo integrado según issue; evidencia aún pendiente |
| [courier-service#3](https://github.com/logitrack-styx/courier-service/issues/3) | OPEN / PENDIENTE | P1 / alto | courier-service/src; docker-compose.yml | Fase 3 — Dockerizar servicios y configurar Docker Compose: tests de dominio, MySQL y flujo integrado según issue; evidencia aún pendiente |
| [courier-service#2](https://github.com/logitrack-styx/courier-service/issues/2) | OPEN / PENDIENTE | P1 / alto | courier-service/src; docker-compose.yml | Fase 2 — Implementar courier-service y gestión de repartidores: tests de dominio, MySQL y flujo integrado según issue; evidencia aún pendiente |
| [S3-19 security-service#14](https://github.com/logitrack-styx/security-service/issues/14) | OPEN / PENDIENTE | P0 / alto | security-service/src/main/java/com/logitrack/security | Procedimiento reproducible de primer ADMIN verificado o deuda abierta declarada ; Issuer JWT homogéneo en tests y producción ; Contrato de login documentado y probado ; No se marca como corregido lo no verificado |
| [S3-13 security-service#13](https://github.com/logitrack-styx/security-service/issues/13) | OPEN / PENDIENTE | P0 / alto | api-gateway/src/main/java/com/logitrack/gateway/config/SecurityConfig.java | Mismos permisos de Sprint 2 por método/path ; CLIENTE asignando→403, ADMIN válido autorizado ; Reglas propietario siguen en Courier/Shipment ; Deny-by-default para métodos y rutas desconocidos |
| [S3-12 security-service#12](https://github.com/logitrack-styx/security-service/issues/12) | OPEN / PENDIENTE | P0 / alto | api-gateway/src/main/java/com/logitrack/gateway/config/SecurityConfig.java | Token válido RS256 y issuer aceptados ; Ausente, adulterado, vencido, issuer/claims inválidos→401 ; Roles convertidos `ROLE_*` correctamente ; Downstream mantiene seguridad local y no se filtran tokens |
| [S3-04 security-service#11](https://github.com/logitrack-styx/security-service/issues/11) | CLOSED / PENDIENTE | P1 / alto | security-service/pom.xml | `SECURITY-SERVICE` aparece UP en Eureka ; Login y `/users/me` mantienen contrato actual ; Tests/arranque verificados |
| [S3-00 shipment-service#56](https://github.com/logitrack-styx/shipment-service/issues/56) | CLOSED / PENDIENTE | P1 / alto | shipment-service/docs/SPRINT3-PLANNING.md | Contrastar planificación, fuentes y Definition of Done con evidencias reales |
| [S3-21 shipment-service#55](https://github.com/logitrack-styx/shipment-service/issues/55) | OPEN / PENDIENTE | P2 / medio | shipment-service/docs/sprint3 | Video/review enseñan funcionalidad principal andando ; Estado completo/parcial y pendientes reflejan pruebas reales ; Código mostrado es el mínimo relevante ; Evidencia y enlaces a PR/tests disponibles |
| [S3-18 shipment-service#54](https://github.com/logitrack-styx/shipment-service/issues/54) | OPEN / PENDIENTE | P1 / alto | shipment-service/src/test; colección Apidog | S3-01..S3-20 tienen evidencia de ejecución y resultado ; 100% de aserciones definidas pasan ; Fallo no modifica envío ni rompe disponibilidad de Shipment ; Resultados, reportes, logs y capturas sin secretos |
| [S3-17 shipment-service#53](https://github.com/logitrack-styx/shipment-service/issues/53) | OPEN / PENDIENTE | P1 / alto | logitrack-sprint3-apidog-collection.json | La colección completa utiliza solo Gateway ; Token/UUID faltante provoca fallo explícito ; Respuestas JSON, HTTP y negocio verificadas ; La suite se puede ejecutar nuevamente sin contaminación accidental |
| [S3-09 shipment-service#52](https://github.com/logitrack-styx/shipment-service/issues/52) | CLOSED / PENDIENTE | P1 / alto | shipment-service/src/test/java/com/logitrack/shipment/service/impl | Tests unitarios y de integración ejecutados y verdes ; 404→404, inactivo→409, fallo infraestructura→503, contrato inválido→502 ; No se asigna ante errores ; Concurrencia permite una sola asignación válida |
| [S3-08 shipment-service#51](https://github.com/logitrack-styx/shipment-service/issues/51) | CLOSED / PENDIENTE | P1 / alto | shipment-service/src/main/java/com/logitrack/shipment/client | No usar `disponible=false` como fallback técnico ; 404 de negocio no se convierte a 503 ni cuenta como caída ; Circuit breaker habilitado y configurable, con excepciones correctas ; En todos los errores el envío conserva estado e identidad |
| [S3-07 shipment-service#50](https://github.com/logitrack-styx/shipment-service/issues/50) | CLOSED / PENDIENTE | P1 / alto | shipment-service/src/main/java/com/logitrack/shipment/client | Asignación consulta vía Feign y nombre lógico ; Se mantienen UUID, DTO, Bearer, reglas de estado y atomicidad ; No queda RestClient de Shipment→Courier en producción ; Asignación positiva verificada |
| [S3-06 shipment-service#49](https://github.com/logitrack-styx/shipment-service/issues/49) | CLOSED / PENDIENTE | P1 / alto | shipment-service/pom.xml | `SHIPMENT-SERVICE` aparece UP ; Compilación y pruebas previas verdes ; No se introduce dependencia física nueva |
| [S3-02 styx-config#5](https://github.com/logitrack-styx/styx-config/issues/5) | CLOSED / PENDIENTE | P1 / alto | styx-config/*.yaml | Se preservan las configuraciones actuales tras reconciliar S3-01 ; Config Server entrega los YAML de ambos servicios nuevos y los clientes ; `eureka.instance` es hermano de `eureka.client`, Gateway usa `spring.cloud.gateway.server.webflux.*` ; No se publican secretos |
| [S3-01 styx-config#4](https://github.com/logitrack-styx/styx-config/issues/4) | CLOSED / PENDIENTE | P1 / alto | styx-config/*.yaml | Divergencias registradas y resueltas deliberadamente. ; Configuración compatible con los servicios en `develop`. ; No se publicaron `.env`, contraseñas ni claves privadas. ; Config Server sirve las propiedades efectivas esperadas.  **Referencia:** Contrato técnico definitivo Sprint 3 v1.2, §§5.1, 7 y 8. **Dependencias:** ninguna; desbloquea configuración de Eureka/Gateway. |

## Hallazgos reproducibles y frentes adicionales

| Requisito | Archivo | Hallazgo estático | Aceptación |
|---|---|---|---|
| JWT/TTL | security-service/.../JwtServiceImp.java; api-gateway/.../SecurityConfig.java | TTL literal 3600; issuer recibido por Gateway sin validator explícito; anyExchange authenticated | Firma RS256 real y negativos de claims/issuer/exp/alg, TTL configurable, 401/403 |
| Cliente | shipment-service/.../model/Envio.java | Solo UUID clienteId; entidad Cliente ausente | ADR, backfill y FK interna, aislamiento A/B, ADMIN y REPARTIDOR |
| Paginación | shipment-service/.../EnvioServiceImpl.java; courier-service/.../CourierServiceImpl.java | findAll sin Pageable | Límites, orden estable, filtros, vacío, negativos y máximo |
| Swagger | courier-service/pom.xml; tres APIs | Courier sin springdoc; otras declaraciones no certifican ejecución | /v3/api-docs válido y UI real en las tres APIs |
| Deuda Courier | courier-service/src/main/resources/application.yaml | ddl-auto update por defecto | Migración y validación con datos existentes |
| Docker | docker-compose.yml | Tags propios 0.3.0 sin procedencia verificada; contraseñas por defecto en Compose | Imágenes trazables, configuración sin defaults inseguros, nueve saludables y recuperación |
| PUT/POST | shipment-service/.../controller/AsignacionController.java | PUT actual; semántica de repetición por verificar | ADR y prueba de repetición/concurrencia |

## Orden de ejecución

1. Completar revisión de comentarios/PRs, fuentes y resultados de suites.
2. JWT/RBAC/TTL con tests negativos y sin alterar cambios locales previos.
3. Infraestructura versionada y arranque Docker sin borrar volúmenes existentes.
4. ADR Cliente y migración/backfill, pertenencia y regresión.
5. ADR PUT/POST, paginación y Swagger.
6. Apidog con nombres actuales, 7 pruebas A y 20 E2E ejecutadas.
7. Fichas individuales y cierre remoto solo con evidencia; ninguna autorización técnica convierte una prueba pendiente en aprobada.

Actualización: Docker Desktop pudo iniciarse (motor 29.8.0). El bloqueo inicial queda como evidencia histórica, no como impedimento vigente. Ver iteracion-01.md y logs posteriores.



## Resultado final del relevamiento ejecutado

- Docker Desktop disponible, motor 29.8.0. Compose existente: nueve contenedores saludables tras `docker compose up -d --wait --wait-timeout 180` (exit 0). No se borraron volúmenes.
- Eureka: API-GATEWAY, SECURITY-SERVICE, COURIER-SERVICE y SHIPMENT-SERVICE UP; evidencia eureka-registry.json.
- Shipment repetido con Docker: BUILD SUCCESS, 86 tests, cero fallos, errores y omitidos. El error anterior queda registrado como limitación inicial resuelta.
- Config: BUILD SUCCESS, No tests to run. No equivale a suite funcional.
- Swagger interno sin autenticación: Security y Shipment devuelven HTTP 200 en /v3/api-docs y /swagger-ui/index.html; Courier devuelve 401 en ambas rutas. Falta validar contenido OpenAPI y UI visual: no se marca Swagger VALIDADO.
- Arranque y registro corresponden a imágenes existentes 0.3.0, no al cambio JWT local. No se verificaron procedencia SHA/digest ni publicación de las imágenes.
- No ejecutados: recuperación Courier, 7+20 escenarios Apidog/E2E, negativos HTTP del Gateway y migraciones Cliente. No hay cierres, commits ni PR nuevos.
- Fase 0 tiene inventario y ejecución de baseline, pero sigue PENDIENTE de contrastar fuentes normativas ausentes y revisar evidencia individual de PRs. Los demás frentes siguen PENDIENTES, salvo corrección de decoder verificada al nivel indicado.
