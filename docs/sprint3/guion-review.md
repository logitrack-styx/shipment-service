# Sprint Review — guion equivalente de 14 minutos

Este archivo es un guion de defensa; no afirma que se haya grabado o presentado un video. Mostrar evidencias reales y las ramas/PRs desde GitHub, sin capturas de secretos ni tokens completos.

| Tiempo | Demostración y explicación |
|---|---|
| 00:00–01:00 | Goal: Gateway único, Eureka y Feign sin alterar autonomía ni permisos. Mostrar siete repositorios, ramas, commits y PRs. S3-00/01: la auditoría verificó SHA y divergencia antes de editar; no se usó el estado CLOSED como prueba. |
| 01:00–03:00 | S3-02..06: Config consume Git, Eureka se inicia después y registra Security/Courier/Shipment/Gateway. Mostrar nueve saludables, registro UP y versión de configuración del reporte. Explicar que depends_on ordena arranque pero no resuelve caídas posteriores. |
| 03:00–05:00 | S3-10..14: Gateway reescribe /api/v1/shipments a /envios y usa lb:// por nombre. Mostrar JWT RS256, issuer, sub UUID, roles, exp y TTL configurable de 3600 por compatibilidad. Privada solo en Security. Distinguir 401 inválido de 403 sin permiso, con negativos de los reportes. Mostrar denegación de ruta/método desconocidos y CORS permitido/rechazado. No adivinar qué significa SCL. |
| 05:00–07:00 | S3-07..09: asignación PUT consulta disponibilidad por OpenFeign. Explicar 404 inexistente, 409 inactivo/conflicto, 503 caída y 502 respuesta/autorización externa inválida, apoyado en suites. Mostrar concurrencia 200/409 y actualización atómica, versión y auditoría. La repetición no crea doble asignación. |
| 07:00–09:00 | Recuperación de Cliente: entidad de Shipment con identidad logística, sin credenciales ni roles locales. CLIENTE usa sub e ignora clienteId arbitrario; ADMIN valida destinatario existente; REPARTIDOR no adquiere derechos de Cliente. Explicar V4: backfill primero, FK interna después, preservación de históricos y restauración desde backup como rollback operativo. Mostrar aislamiento A/B. |
| 09:00–10:00 | Paginación: arrays compatibles, page/size, máximo 100, filtros y orden estable; totales en headers. Mostrar páginas vacías y límites inválidos. Contrastar bytes/filas y tiempos medidos en scale-report; son mediciones locales que incluyen docker exec, no un benchmark productivo ni una medición de heap Java. |
| 10:00–11:00 | Swagger de las tres APIs: encabezados, operaciones, Bearer y schemas. Courier ya documenta estados, propiedad y paginación. Mostrar OpenAPI real y las observaciones visuales; no confundir anotaciones con una UI cargada. |
| 11:00–12:00 | S3-15/16 y herencia Courier #3/#6: imágenes multietapa, JRE sin Maven, usuario no root, secretos externos, MySQL aislado y volúmenes persistentes. Mostrar digests publicados y pull real. Corte/reinicio Courier y recuperación por Feign/Gateway; reinicio Security/BD sin perder envíos ni duplicar ADMIN. |
| 12:00–13:00 | S3-17/18 y Courier #2/#4: ejecutar colección vigente conservando las doce variables a*. Mostrar siete requests y 16 aserciones; explicar preparación dinámica y reporte depurado. Mostrar los veinte grupos E2E y sus comprobaciones, no inventar que el runner HTTP fue la app de escritorio. |
| 13:00–14:00 | S3-19: bootstrap idempotente, login email/nombre compatible con username, cuentas deshabilitadas rechazadas. S3-20: RestClient Courier→Security solo para alta con timeouts y ddl-auto:update explícitamente limitado a demo; no afirmar production-ready. S3-21: recorrer matriz de fichas, PRs, evidencia y estado GitHub final. RabbitMQ, Kubernetes, emails y otras evoluciones siguen fuera. |

## Respuestas breves para defensa

- RS256 firma con una privada y permite verificación local con públicas; no cifra contenido ni transporte. TTL no es refresh token ni revocación inmediata.
- Cliente es un concepto logístico local. Usuario autentica en Security. La FK clientes/envios vive en una sola BD; la identidad entre servicios se vincula lógicamente por UUID.
- PUT conserva el recurso conocido de repartidor asignado. Responder 409 a repetición mantiene el mismo efecto y no crea una segunda asignación.
- Page en SQL evita traer todos los registros para luego cortar en Java. El máximo se valida antes de consultar.
- Un build aprobado no demuestra Eureka UP, salud de nueve contenedores ni un E2E aprobado. Cada uno tiene su evidencia separada.
- Una demora eventual de descubrimiento se comprueba hasta recuperación tanto del consumidor Feign como de Gateway; no se oculta un fallo intermedio como un éxito inmediato.
- Si un check, permiso de integración o criterio queda pendiente, se informa como tal; el objetivo administrativo de cero issues no autoriza cierres sin pruebas.
