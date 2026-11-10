# Solicitudes asíncronas de asignación

En Sprint 3, el PUT verifica disponibilidad y asigna mediante una actualización condicional. Sprint 4 agrega un POST administrativo que devuelve 202 con una operación PENDING y un GET que permite consultar su estado por envío y operación.

Shipment bloquea la fila del envío durante la creación para serializar dos solicitudes concurrentes. La clave de idempotencia es única y se compara con el SHA-256 de los UUID canónicos de envío y repartidor. La solicitud y el registro de outbox se confirman en la misma transacción, sin depender del broker. La tabla de outbox referencia la solicitud para conservar los datos de correlación.

El PUT conserva su actualización condicional. Si un PUT asigna el envío después de que se acepta un POST, el consumidor deberá aplicar la misma condición antes de confirmar la asignación asíncrona.

Para activar el transporte en Shipment, configurá `ASSIGNMENT_MESSAGING_ENABLED=true` y las variables `SPRING_RABBITMQ_*` del entorno. El worker toma una concesión de base de datos antes de publicar y libera la transacción antes de esperar la confirmación del broker. La concesión vence a los 60 segundos. Se reintenta hasta cinco veces y se conserva el mismo identificador de evento para que Courier pueda deduplicar. Si se agotan los intentos o Courier no responde dentro de cinco minutos después de la confirmación, la solicitud queda FAILED.

La cola durable `shipment.assignment.eligibility.v1` recibe las respuestas de elegibilidad. Las respuestas inválidas o que agotan los reintentos del consumidor van a `shipment.assignment.eligibility.v1.dlq`. Shipment confirma la recepción después de persistir el resultado y conserva el estado de una operación terminal ante una respuesta tardía.

La verificación local con Java 25 y Docker usa `mvnw.cmd -o "-Dtest=EnvioRepositoryTest,WebMvcSliceSmokeTest,SecurityContractTest" test`.

Con los repositorios Shipment y Courier en directorios hermanos, ejecutá `pwsh -File scripts/test-assignment-messaging.ps1` desde Shipment. El script construye ambas aplicaciones, levanta MySQL y RabbitMQ aislados, y usa un JWT firmado con una clave efímera. Comprueba la recuperación del broker y de Courier, el resultado persistido ante un duplicado, el destino DLQ de un comando inválido y el rechazo cuando el repartidor está inactivo o no existe. La API de administración del broker se abre en un puerto loopback efímero solo para esta prueba. El entorno se elimina al terminar.
