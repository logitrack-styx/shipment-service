# Verificación de backend Sprint 4 v2.1

El contrato HTTP cubre 27 operaciones. La prueba Compose usa Gateway con los servicios reales en modo FAKE. La prueba de Payment en modo TEST usa un proveedor simulado para verificar el POST firmado del webhook sin credenciales externas.

| Operaciones del contrato | Evidencia reproducible |
| --- | --- |
| Login, registro, perfil propio, alta y consulta de usuarios | Smoke HTTP con tres identidades y regresión de Security |
| Listado, alta, consulta, edición, cambio de estado y disponibilidad de repartidores | Smoke HTTP, validación de propiedad y regresión de Courier |
| Alta, listado global, consulta, seguimiento, PUT de asignación, cambio de estado y cancelación de envíos | Smoke HTTP y regresión de Shipment con MySQL |
| Listados propios y asignados, POST y GET de solicitud de asignación | Smoke HTTP con dos propietarios, paginación, control de roles y RabbitMQ real |
| Alta y consulta de cotización, checkout y consulta de pago | Smoke HTTP en modo FAKE y pruebas de Payment con idempotencia, expiración y propiedad |
| Webhook Mercado Pago | Pruebas de Payment con firma válida e inválida, deduplicación, consulta simulada del proveedor y recuperación |

## Ejecución local

- Java 25. Ejecutar `./mvnw test` desde Gateway, Security, Courier, Shipment y Payment. Las pruebas de Shipment requieren Docker para MySQL.
- Ejecutar `pwsh -File scripts/test-sprint4-backend.ps1` desde Shipment. El Compose usa proyectos y credenciales efímeros. Ver [configuración](backend-compose.md).
- Ejecutar `pwsh -File scripts/test-assignment-messaging.ps1` para recuperación de broker, reentrega y DLQ. Las colas son del proyecto de pruebas.

La regresión de Gateway, Courier, Shipment y Payment ejecutó 8, 22, 91 y 12 pruebas respectivamente. Security ejecutó 7 y omitió la prueba SMTP opcional en esa pasada. La entrega SMTP se verificó por separado con Mailpit real. Las pruebas de paginación comprueban tamaño y totales de la respuesta con datos de dos propietarios.

## Hitos separados

La verificación MP-01 a MP-07 contra Mercado Pago requiere credenciales oficiales TEST y el webhook HTTPS. El entorno de verificación local no dispone de esa configuración. El smoke backend usa FAKE. La prueba conjunta con frontend se mantiene como hito separado. [Seguimiento externo de Mercado Pago](https://github.com/logitrack-styx/payment-service/issues/7)
