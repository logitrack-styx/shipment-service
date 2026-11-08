# ADR 003 — Conservar PUT de asignación

Decisión: conservar `PUT /api/v1/shipments/{id}/repartidor`, traducido a `/envios/{id}/repartidor`. Se fija la asociación de un recurso conocido; no se crea un recurso de orden independiente. Cambiar a POST rompería clientes sin aportar una semántica necesaria al Sprint 3.

La implementación solo permite asignar una vez mientras el envío está PENDIENTE. La actualización condicional comprueba estado y ausencia de repartidor en una única sentencia, aumenta versión y actualiza auditoría. Una repetición idéntica recibe 409 y deja el mismo estado; idempotencia HTTP exige igual efecto, no necesariamente igual respuesta. Dos peticiones concurrentes no deben crear dos asignaciones: una gana y la otra recibe conflicto.

La consulta Feign ocurre antes de la transacción de actualización y no modifica datos si Courier falla. Los tests de repositorio y E2E deben comprobar repetición, concurrencia y conservación de estado ante caída. Este ADR no acredita por sí solo esa verificación.

Fuente: RFC 9110, secciones 9.2.2 y 9.3.4: https://www.rfc-editor.org/rfc/rfc9110.html .
