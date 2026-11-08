# Matriz de cierre — evidencia ejecutada

Estado técnico: VALIDADO en las ramas de cierre. Integración y cierre administrativo se acreditan separadamente con snapshots de GitHub; no se deducen de estas pruebas.

El acta administrativa final y los cierres individuales se consultan en [S3-21 / shipment-service#55](https://github.com/logitrack-styx/shipment-service/issues/55). Este documento conserva la distinción entre prueba técnica y estado remoto.

Ejecución 2026-11-08: 55 comprobaciones E2E; 16 aserciones Apidog; 36 comprobaciones directas de JWT/email. Nueve contenedores saludables y seis imágenes publicadas/descargadas por digest.

## Pruebas automatizadas

| Repositorio | Tests | Fallos | Errores |
|---|---:|---:|---:|
| security-service | 6 | 0 | 0 |
| courier-service | 20 | 0 | 0 |
| shipment-service | 87 | 0 | 0 |
| api-gateway | 2 | 0 | 0 |
| eureka-server | 1 | 0 | 0 |
| config-server | 0 | 0 | 0 |

Config no tiene tests existentes: su build no se presenta como suite funcional. Su comportamiento se verificó por Config Server, Docker y E2E.

## Fichas por issue

### S3-14 — config-server#8

- Problema: CORS y errores perimetrales.
- Implementación y motivo: `api-gateway/.../SecurityConfig.java`; mantiene separación de dominios y contrato vigente.
- Verificación: E2E preflight permitido, origen rechazado y JSON 401/403.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/config-server/issues/8); estado administrativo inicial: OPEN.

### S3-11 — config-server#7

- Problema: Rutas lb y reescritura.
- Implementación y motivo: `styx-config/api-gateway.yaml`; mantiene separación de dominios y contrato vigente.
- Verificación: E2E auth/users/couriers/shipments; StripPrefix y RewritePath efectivos.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/config-server/issues/7); estado administrativo inicial: OPEN.

### S3-10 — config-server#6

- Problema: Gateway como entrada única.
- Implementación y motivo: `api-gateway/src/main; styx-config/api-gateway.yaml`; mantiene separación de dominios y contrato vigente.
- Verificación: runtime: puertos de negocio internos y registro UP; E2E por 8066.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/config-server/issues/6); estado administrativo inicial: CLOSED.

### S3-03 — config-server#5

- Problema: Descubrimiento Eureka.
- Implementación y motivo: `eureka-server/src/main`; mantiene separación de dominios y contrato vigente.
- Verificación: runtime-report.json: cuatro aplicaciones UP.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/config-server/issues/5); estado administrativo inicial: CLOSED.

### S3-20 — courier-service#20

- Problema: Decisión de deuda Courier.
- Implementación y motivo: `courier-service/.../SecurityServiceUsuarioRolVerifier.java; ADR-004`; mantiene separación de dominios y contrato vigente.
- Verificación: RestClient acotado al alta con timeouts; ddl-auto:update justificado para demo, no producción.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/courier-service/issues/20); estado administrativo inicial: OPEN.

### S3-16 — courier-service#19

- Problema: Arranque, caída y recuperación.
- Implementación y motivo: `scripts/sprint3_e2e.py; Compose`; mantiene separación de dominios y contrato vigente.
- Verificación: E2E S3-19/20: caída 503 sin escritura y recuperación Feign/Gateway.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/courier-service/issues/19); estado administrativo inicial: OPEN.

### S3-15 — courier-service#18

- Problema: Compose global e imágenes trazables.
- Implementación y motivo: `compose.sprint3.yml; compose.sprint3.release.yml; seis Dockerfiles`; mantiene separación de dominios y contrato vigente.
- Verificación: runtime nueve saludables, no root, sin puertos internos; push/pull seis imágenes.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/courier-service/issues/18); estado administrativo inicial: OPEN.

### S3-05 — courier-service#17

- Problema: Courier como cliente Eureka.
- Implementación y motivo: `courier-service/pom.xml; src/main/resources`; mantiene separación de dominios y contrato vigente.
- Verificación: registro UP, disponibilidad y recuperación en E2E.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/courier-service/issues/17); estado administrativo inicial: CLOSED.

### Courier heredado #6 — courier-service#6

- Problema: MySQL y entorno.
- Implementación y motivo: `Compose y configuración Courier`; mantiene separación de dominios y contrato vigente.
- Verificación: BD aislada, sin puerto host, secretos externos y persistencia.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/courier-service/issues/6); estado administrativo inicial: OPEN.

### Courier heredado #4 — courier-service#4

- Problema: Integración completa.
- Implementación y motivo: `scripts/sprint3_e2e.py`; mantiene separación de dominios y contrato vigente.
- Verificación: 55 comprobaciones E2E y recuperación real.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/courier-service/issues/4); estado administrativo inicial: OPEN.

### Courier heredado #3 — courier-service#3

- Problema: Dockerización integrada.
- Implementación y motivo: `compose.sprint3.release.yml`; mantiene separación de dominios y contrato vigente.
- Verificación: seis imágenes publicadas y nueve componentes saludables.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/courier-service/issues/3); estado administrativo inicial: OPEN.

### Courier heredado #2 — courier-service#2

- Problema: Gestión de repartidores.
- Implementación y motivo: `courier-service/src/main`; mantiene separación de dominios y contrato vigente.
- Verificación: suite Courier 20 y E2E alta/consulta/estado/propiedad.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/courier-service/issues/2); estado administrativo inicial: OPEN.

### S3-19 — security-service#14

- Problema: Deuda de identidad S2.
- Implementación y motivo: `security-service/.../AdminBootstrap.java; AuthServiceImpl.java`; mantiene separación de dominios y contrato vigente.
- Verificación: suite Security, login por email en MySQL, errores comunes UTC, bootstrap único después de reinicio, TTL.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/security-service/issues/14); estado administrativo inicial: OPEN.

### S3-13 — security-service#13

- Problema: RBAC por método/ruta y propiedad.
- Implementación y motivo: `api-gateway/.../SecurityConfig.java; servicios de dominio`; mantiene separación de dominios y contrato vigente.
- Verificación: E2E 401/403, ruta/método desconocidos, CLIENTE ajeno y REPARTIDOR.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/security-service/issues/13); estado administrativo inicial: OPEN.

### S3-12 — security-service#12

- Problema: Contrato JWT RS256.
- Implementación y motivo: `api-gateway/.../SecurityConfig.java; decoders de las tres APIs`; mantiene separación de dominios y contrato vigente.
- Verificación: E2E negativos Gateway, downstream-jwt-report.json y JwtDecoderContractTest.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/security-service/issues/12); estado administrativo inicial: OPEN.

### S3-04 — security-service#11

- Problema: Security como cliente Eureka.
- Implementación y motivo: `security-service/pom.xml; src/main/resources`; mantiene separación de dominios y contrato vigente.
- Verificación: registro UP y login/usuarios por Gateway en E2E.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/security-service/issues/11); estado administrativo inicial: CLOSED.

### S3-00 — shipment-service#56

- Problema: Planificación y DoD verificable.
- Implementación y motivo: `docs/SPRINT3-PLANNING.md; docs/sprint3`; mantiene separación de dominios y contrato vigente.
- Verificación: auditoria-inicial.md, fichas y reportes de esta ejecución.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/shipment-service/issues/56); estado administrativo inicial: CLOSED.

### S3-21 — shipment-service#55

- Problema: Review y defensa.
- Implementación y motivo: `docs/sprint3/guion-review.md; fichas y matriz`; mantiene separación de dominios y contrato vigente.
- Verificación: guion equivalente de 14 minutos con pruebas reales y limitaciones explícitas.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/shipment-service/issues/55); estado administrativo inicial: OPEN.

### S3-18 — shipment-service#54

- Problema: Matriz de veinte escenarios.
- Implementación y motivo: `scripts/sprint3_e2e.py`; mantiene separación de dominios y contrato vigente.
- Verificación: 55 comprobaciones, incluyendo los veinte grupos S3-01..20, runtime y persistencia.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/shipment-service/issues/54); estado administrativo inicial: OPEN.

### S3-17 — shipment-service#53

- Problema: Colección Apidog vigente.
- Implementación y motivo: `docs/sprint3/apidog-coleccion.json; scripts/run-apidog.mjs`; mantiene separación de dominios y contrato vigente.
- Verificación: siete requests y scripts originales; doce variables exactas; 16 aserciones.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/shipment-service/issues/53); estado administrativo inicial: OPEN.

### S3-09 — shipment-service#52

- Problema: Asignación atómica y concurrencia.
- Implementación y motivo: `src/main/java/com/logitrack/shipment/repository/EnvioRepository.java`; mantiene separación de dominios y contrato vigente.
- Verificación: suite MySQL y E2E: resultados concurrentes 200/409 sin doble asignación.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/shipment-service/issues/52); estado administrativo inicial: CLOSED.

### S3-08 — shipment-service#51

- Problema: Traducción de fallos y resiliencia.
- Implementación y motivo: `src/main/java/com/logitrack/shipment/client`; mantiene separación de dominios y contrato vigente.
- Verificación: suite CourierClient, E2E 404/409/503 y recuperación.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/shipment-service/issues/51); estado administrativo inicial: CLOSED.

### S3-07 — shipment-service#50

- Problema: Feign por nombre de servicio.
- Implementación y motivo: `src/main/java/com/logitrack/shipment/client`; mantiene separación de dominios y contrato vigente.
- Verificación: E2E S3-08: asignación consultando Courier, sin URL fija.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/shipment-service/issues/50); estado administrativo inicial: CLOSED.

### S3-06 — shipment-service#49

- Problema: Shipment como cliente Eureka.
- Implementación y motivo: `shipment-service/pom.xml; src/main/resources`; mantiene separación de dominios y contrato vigente.
- Verificación: registro UP y flujo de envíos en E2E.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/shipment-service/issues/49); estado administrativo inicial: CLOSED.

### S3-02 — styx-config#5

- Problema: Configuración externa por servicio.
- Implementación y motivo: `styx-config/*.yaml`; mantiene separación de dominios y contrato vigente.
- Verificación: runtime-report.json: Config sirve TTL desde Git, versión registrada.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/styx-config/issues/5); estado administrativo inicial: CLOSED.

### S3-01 — styx-config#4

- Problema: Reconciliación Git de configuración.
- Implementación y motivo: `styx-config/*.yaml`; mantiene separación de dominios y contrato vigente.
- Verificación: fetch sin sobrescrituras y divergencia inicial 0/0 registrada.
- Estado técnico: VALIDADO. [Issue](https://github.com/logitrack-styx/styx-config/issues/4); estado administrativo inicial: CLOSED.

## Evidencias y límites

Reportes: e2e-report.json, apidog-run.json, downstream-jwt-report.json, runtime-report.json, persistence-report.json, restore-report.json, scale-report.json, published-images.json y surefire-summary.json en evidencias/. Los fallos intermedios se registraron y corrigieron; no se presentan como aprobaciones.

Swagger fue observado en navegador en las tres APIs; ver swagger-observacion.md. La colección usa el export que confirmó el usuario y conserva sus doce nombres a*. El runner se identifica como HTTP/Node, no como la app de escritorio Apidog.

Las fuentes S1/S2/S3 y el contrato S2 fueron encontrados y leídos tras ampliar el inventario. No se localizó S3 v1.2 ni la matriz JSON original; los criterios se contrastaron con la consigna oficial S3, cierre v2 aprobado, issues y colección vigente. No se inventa haber leído archivos ausentes.

SCL continúa como término no confirmado por ausencia de grabación/transcripción. Courier ddl-auto:update queda limitado y justificado para demo en ADR-004; no se declara production-ready. No se incorporaron funcionalidades S4.

La aprobación de integración exigida por la revisión automática se registra aparte. Ningún issue se cierra por esta generación de documentación.
