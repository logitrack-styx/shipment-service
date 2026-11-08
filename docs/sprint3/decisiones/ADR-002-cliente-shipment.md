# ADR 002 — Cliente logístico autónomo

Decisión: Shipment posee `Cliente(id UUID, createdDate)`. La identidad lógica coincide con Usuario.id, pero no hay credenciales, rol ni estado de cuenta almacenados como autoridad local. No se copian nombre, email ni teléfono porque el contrato actual registra las direcciones completas por envío y no consume esos perfiles; agregar datos personales sin uso aumentaría duplicación.

Un CLIENTE crea su identidad logística al primer envío utilizando el sub autenticado e ignorando clienteId arbitrario. ADMIN debe indicar un usuario CLIENTE habilitado, verificado contra Security con su Bearer. REPARTIDOR no puede dar de alta envíos. La pertenencia sigue verificándose por sub en consultas y cancelaciones.

La FK es interna a Shipment; se conserva `Envio.clienteId` sin cargar colecciones JPA ni introducir ciclos/N+1. No existe FK a Security. `INSERT IGNORE` sobre la PK permite primeras altas concurrentes sin duplicar el perfil.

V4 crea clientes, recupera cada cliente_id histórico con su fecha más antigua, agrega índices y recién después impone FK. Los históricos se preservan aunque no sea posible contactar Security durante la migración; no se certifica retroactivamente su rol. No se editan V1–V3.

Operación: tomar backup antes de migrar una BD existente; comprobar conteos de propietarios distintos y filas huérfanas. MySQL hace DDL con commits implícitos: rollback operativo es restaurar backup o avanzar con otra migración, nunca editar/borrar el historial aplicado. La migración se prueba primero en MySQL aislado. No se ejecuta sobre datos existentes por presunción.
