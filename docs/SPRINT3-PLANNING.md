# Sprint Planning — LogiTrack / STYX — Sprint 3

**Estado:** backlog creado, implementación pendiente. **Responsables:** ninguno asignado. **Fuente normativa:** `LogiTrack_Contrato_Tecnico_Definitivo_Sprint_3_v1.2.md` (documento acordado por el equipo).

## Sprint Goal
Migrar LogiTrack de 7 a 9 contenedores incorporando Eureka Server, API Gateway como única entrada de negocio y OpenFeign con resiliencia en Shipment → Courier. Mantener intactas las garantías de seguridad y dominio de Sprint 2, validar mediante escenarios ejecutados y preparar Sprint Review.

## Alcance / secuencia
1. Resolver divergencia de `styx-config` y centralizar YAML.
2. Crear Eureka Server, registrar clientes de dominio.
3. Migrar la comunicación Shipment → Courier a Feign y controlar 404, 409, 502 y 503.
4. Construir Gateway WebFlux, configurar rutas, JWT y RBAC.
5. Integrar nueve contenedores en Docker Compose con aislamiento de puertos.
6. Migrar Apidog, ejecutar 20 escenarios y documentar deuda técnica y evidencia.

## Backlog por fase

### Fase 0
- [S3-01 — Reconciliar configuración local/remota](https://github.com/logitrack-styx/styx-config/issues/4) — `styx-config`
- [S3-02 — Externalizar YAML Eureka y Gateway](https://github.com/logitrack-styx/styx-config/issues/5) — `styx-config`


### Fase 1
- [S3-03 — Crear Eureka Server](https://github.com/logitrack-styx/config-server/issues/5) — `config-server`


### Fase 2
- [S3-04 — Registrar Security en Eureka](https://github.com/logitrack-styx/security-service/issues/11) — `security-service`
- [S3-05 — Registrar Courier en Eureka](https://github.com/logitrack-styx/courier-service/issues/17) — `courier-service`
- [S3-06 — Registrar Shipment en Eureka](https://github.com/logitrack-styx/shipment-service/issues/49) — `shipment-service`


### Fase 3
- [S3-07 — Migrar integración a Feign](https://github.com/logitrack-styx/shipment-service/issues/50) — `shipment-service`
- [S3-08 — ErrorDecoder/Fallback/CircuitBreaker](https://github.com/logitrack-styx/shipment-service/issues/51) — `shipment-service`
- [S3-09 — Pruebas Feign + concurrencia](https://github.com/logitrack-styx/shipment-service/issues/52) — `shipment-service`


### Fase 4
- [S3-10 — Crear API Gateway](https://github.com/logitrack-styx/config-server/issues/6) — `config-server`
- [S3-11 — Rutas dinámicas y RewritePath](https://github.com/logitrack-styx/config-server/issues/7) — `config-server`
- [S3-12 — JWT reactivo en Gateway](https://github.com/logitrack-styx/security-service/issues/12) — `security-service`
- [S3-13 — RBAC por ruta/método](https://github.com/logitrack-styx/security-service/issues/13) — `security-service`
- [S3-14 — CORS y errores perimetrales](https://github.com/logitrack-styx/config-server/issues/8) — `config-server`


### Fase 5
- [S3-15 — Compose global de nueve contenedores](https://github.com/logitrack-styx/courier-service/issues/18) — `courier-service`
- [S3-16 — Startup y recuperación Docker/Eureka](https://github.com/logitrack-styx/courier-service/issues/19) — `courier-service`


### Fase 6
- [S3-17 — Migrar Apidog a Gateway](https://github.com/logitrack-styx/shipment-service/issues/53) — `shipment-service`
- [S3-18 — Ejecutar veinte escenarios](https://github.com/logitrack-styx/shipment-service/issues/54) — `shipment-service`


### Deuda S2
- [S3-19 — Revisar deuda S2: ADMIN/JWT/login](https://github.com/logitrack-styx/security-service/issues/14) — `security-service`
- [S3-20 — Revisar deuda S2: Courier→Security/JPA](https://github.com/logitrack-styx/courier-service/issues/20) — `courier-service`


### Entrega
- [S3-21 — Video y Sprint Review](https://github.com/logitrack-styx/shipment-service/issues/55) — `shipment-service`

## Hitos de validación
- Config Server expone configuración real de Eureka y Gateway, sin secretos.
- Eureka registra `SECURITY-SERVICE`, `COURIER-SERVICE`, `SHIPMENT-SERVICE` y `API-GATEWAY` como `UP`.
- Gateway gestiona JWT RS256 y roles y reescribe rutas a endpoints reales.
- Asignación Courier ACTIVO funciona a través de Feign; inactivo→409, inexistente→404, servicio caído→503, contrato inválido→502; ninguna falla modifica el envío.
- Compose inicia nueve contenedores; puertos de negocio solo internos.
- Suite de 20 escenarios S3-01…S3-20 ejecutada con resultados y evidencia; revisión de deuda S2 sin declarar resuelto lo que no está verificado.

## Definition of Done
Cumplir íntegramente los 12 criterios del §8 del contrato v1.2; PRs integrados a `develop` (servicios) y `main` (`styx-config`); reportes CI/Maven, Apidog, dashboard Eureka y ejecución de Docker adjuntos. El cierre del sprint exige evidencia real, no solamente issues cerradas.

## Riesgos y acuerdos
- `styx-config` del ZIP difiere del remoto: resolver antes de cambios.
- `api-gateway`, `eureka-server` y Compose global aún no disponían de repositorio dedicado al planificar; las issues correspondientes están **provisionalmente** en repositorios existentes y deben trasladarse/enlazarse cuando se creen los repos apropiados.
- No exponer `.env`, tokens ni claves; no borrar volúmenes persistentes sin respaldo/autorización.
- CORS, Circuit Breaker y manejo avanzado de fallos son compromisos técnicos adicionales del contrato, no todo exigido literalmente por la consigna.

## Relaciones
Ver dependencias declaradas en cada issue. Secuencia principal: S3-01 → S3-02 → S3-03 → S3-04/05/06 → S3-07/08/09; S3-10/11/12/13/14 → S3-15/16 → S3-17/18 → S3-21.
