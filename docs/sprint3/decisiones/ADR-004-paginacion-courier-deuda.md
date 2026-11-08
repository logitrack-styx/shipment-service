# ADR 004 — Listas acotadas y deuda Courier

Se conserva el array JSON de listados para no romper la colección existente. `page` desde cero y `size` de 1 a 100 (predeterminado 20) limitan consultas SQL; el orden por id es estable. `X-Total-Count` y `X-Total-Pages` permiten continuar. Los consumidores que dependían de recibir todos los registros deben recorrer páginas: es un cambio de comportamiento documentado, no una truncación oculta. Los adaptadores heredados del servicio también quedan acotados a la primera página.

Se conservan filtros y permisos existentes. Cliente/Repartidor se consultan por recursos con pertenencia; no se habilitan listados nuevos. Los índices locales cliente/estado/id y repartidor/estado/id preparan sus consultas sin cargar relaciones completas.

Courier→Security mantiene RestClient únicamente para verificar el alta de un repartidor, con connect timeout de 2 segundos y read timeout de 3 segundos. La consigna exige OpenFeign para Shipment→Courier, no una migración general de clientes. La validación de JWT continúa siendo local; no se agrega dependencia remota por request de negocio.

`ddl-auto:update` de Courier queda explícitamente limitado al entorno académico de demo; no se declara preparación productiva completa. Antes de un despliegue productivo se requiere introducir baseline Flyway sobre un backup, migraciones de esquema y `validate`, con revisión del esquema histórico. Mantenerlo evita fingir una migración ya probada o baselinar tablas desconocidas. Esta limitación debe exponerse en Review y en S3-20, cuyo criterio permite documentar la deuda.
