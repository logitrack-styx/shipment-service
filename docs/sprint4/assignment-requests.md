# Solicitudes asíncronas de asignación

En Sprint 3, el PUT verifica disponibilidad y asigna mediante una actualización condicional. Sprint 4 agrega un POST administrativo que devuelve 202 con una operación PENDING y un GET que permite consultar su estado por envío y operación.

Shipment bloquea la fila del envío durante la creación para serializar dos solicitudes concurrentes. La clave de idempotencia es única y se compara con el SHA-256 de los UUID canónicos de envío y repartidor. La solicitud y el registro de outbox se confirman en la misma transacción, sin depender del broker. La tabla de outbox referencia la solicitud para conservar los datos de correlación.

El PUT conserva su actualización condicional. Si un PUT asigna el envío después de que se acepta un POST, el consumidor deberá aplicar la misma condición antes de confirmar la asignación asíncrona.

La verificación local con Java 25 y Docker usa `mvnw.cmd -o "-Dtest=EnvioRepositoryTest,WebMvcSliceSmokeTest,SecurityContractTest" test`.
