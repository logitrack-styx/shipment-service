# Compose de backend para Sprint 4

El overlay `compose.sprint4.backend.yml` se aplica después de `compose.sprint3.release.yml`. Conserva Config Server y Eureka de la línea base y construye las aplicaciones modificadas desde sus repositorios hermanos. Usa un proyecto Docker distinto, con volúmenes propios.

## Checkouts y configuración

| Directorio hermano | Rama Sprint 4 |
| --- | --- |
| `api-gateway` | `sprint4/gateway-routing` |
| `courier-service` | `sprint4/courier-assignment-events` |
| `security-service` | `sprint4/mail-observability` |
| `payment-service` | `sprint4/payment-container` |
| `styx-config` | `sprint4/payment-route` |

Config Server lee el checkout de `styx-config` en modo native. Los servicios con varias redes anuncian en Eureka el nombre DNS del servicio, accesible desde la red compartida. Gateway escucha en `127.0.0.1:8066`. Mailpit escucha en loopback en el puerto 8025 con los perfiles locales `dev` y `test`. RabbitMQ y MySQL de Payment tienen redes internas. Shipment y Courier usan credenciales distintas y permisos acotados a sus colas y al exchange compartido.

Ejecutá desde Shipment con Docker Compose v2 y PowerShell 7:

```powershell
$envFile = ./scripts/prepare-sprint4-local.ps1 -Directory (Join-Path ([IO.Path]::GetTempPath()) 'logitrack-s4-local')
docker compose --env-file $envFile --profile dev -f compose.sprint3.release.yml -f compose.sprint4.backend.yml config --quiet
docker compose --env-file $envFile --profile dev -f compose.sprint3.release.yml -f compose.sprint4.backend.yml up --build -d --wait
```

El generador escribe las credenciales y las claves fuera del checkout. `.env.test.example` documenta los parámetros de infraestructura. Usá un directorio nuevo para generar credenciales y conservá el mismo archivo de entorno durante el ciclo de vida del stack. Los volúmenes persistentes y las definiciones de RabbitMQ deben corresponder a esas credenciales.

El modo FAKE está activado en el entorno generado. Para usar Checkout TEST externo, cargá los valores oficiales del panel Prueba y configurá la URL pública HTTPS del webhook. Payment rechaza `MP_ENV=PRODUCTION` al iniciar.

## Prueba y rollback

`pwsh -File scripts/test-sprint4-backend.ps1` crea un proyecto efímero. Usa registro e inicio de sesión reales, prueba los preflight y el rechazo de otro origen, recorre dos páginas de envíos propios, confirma una asignación RabbitMQ, crea y consulta un pago FAKE y busca el correo en Mailpit.

Para detener el Sprint 4 conservando sus datos, ejecutá `down` con los mismos archivos y perfil del comando de arranque. La línea base se levanta por separado con `compose.sprint3.release.yml` y su configuración del Sprint 3.
