# Verificación visual de Swagger

2026-11-08. Las tres interfaces se abrieron en navegador a través del proxy temporal de documentación en 127.0.0.1:8099. Se obtuvieron capturas reales en la conversación de ejecución; no se simuló una UI ni se usó el mero estado HTTP como prueba visual.

| API | Encabezado observado | Contenido visible |
|---|---|---|
| Security | LogiTrack Security API — Sprint 3 — OAS 3.1 | Usuarios: POST /users, GET /users/{id}, GET /users/me. Autenticación: register/login. Schemas de request/response y Authorize. |
| Courier | LogiTrack Courier API — 3.0 — OAS 3.1 | Perfiles, reglas ADMIN/propietario, GET paginado con máximo 100, create/update/estado/disponibilidad; Authorize y schemas. |
| Shipment | LogiTrack Shipment API — Sprint 3 — OAS 3.1 | Envíos, ciclo de vida, pertenencia y PUT de asignación; Authorize y schemas. |

No se ingresaron credenciales ni tokens en la interfaz. Las operaciones de negocio se verifican por Gateway con los runners. El selector Servers muestra el puerto interno de cada API, por lo que el proxy se usa para lectura documental; no representa una exposición pública de los servicios. Los documentos OpenAPI efectivos y la prueba de sus rutas se conservan en `evidencias/openapi-*.json` y `runtime-report.json`.
