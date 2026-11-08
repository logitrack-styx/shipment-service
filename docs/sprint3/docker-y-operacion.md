# Docker, versiones y operación reproducible

El Compose versionado es `shipment-service/compose.sprint3.yml`. Se usa el proyecto separado `logitrack-s3-close`, con tres volúmenes nuevos; los volúmenes de la línea base `logitrack` se conservan. Solo Gateway publica entrada de negocio en loopback 8066. Config y Eureka publican diagnóstico en loopback 8888 y 8065; APIs y MySQL no tienen puertos de host.

| Base | Servicios | Motivo y alternativa evaluada |
|---|---|---|
| eclipse-temurin:25-jdk | Security, Courier, Gateway, Eureka, Config | Compila el release Java 25 configurado en los POM; usar JDK 21 requeriría cambiar el objetivo de compilación. Temurin evita depender del JDK comercial instalado en el host. |
| maven:3-eclipse-temurin-25 | Build Shipment | Incluye Maven 3 y JDK 25 para el build multietapa existente. Los otros servicios fijan Maven mediante wrapper. No se afirma que Maven 3 sea una versión exacta: el digest resuelto está en el build real. |
| eclipse-temurin:25-jre | Las seis aplicaciones | Ejecuta bytecode Java 25 sin Maven ni compilador. Un JDK completo aumentaría el runtime sin necesidad funcional. Alpine no se adopta para evitar un cambio adicional de libc y herramientas de salud en este cierre. |
| mysql:8.4 | Tres bases independientes y pruebas | Conserva la serie de mantenimiento del esquema existente. Cambiar a MySQL Innovation o a otro motor ampliaría el alcance y exigiría migraciones distintas. |

No se usa `latest` en el Compose versionado. Las bases por versión mayor/serie pueden cambiar: se conservan los digests efectivamente resueltos en extractos de build y los IDs reales en `evidencias/runtime-report.json`. Las imágenes propias locales usan `s3-<digest de fuentes>` y la etiqueta `org.logitrack.source.sha256`; `image-sources.json` relaciona fuentes, commit e imagen. Se publicaron seis etiquetas nuevas `facutolaba/logitrack-<servicio>:0.3.1-s3-<digest de fuentes>` y se descargó cada artefacto por su digest inmutable: `evidencias/published-images.json` registra el resultado real. Los tags previos 0.3.0 se preservaron.

Cada Dockerfile compila en una etapa y copia únicamente el JAR al JRE; curl permite healthchecks efectivos. Los procesos se ejecutan con appuser y se comprueba el UID en runtime. Los secretos se montan desde archivos externos; las contraseñas requeridas no tienen valores inseguros predeterminados. `.env.sprint3.example` contiene nombres y campos vacíos, no credenciales.

## Reproducción desde siete repositorios hermanos

Ubicar los siete clones en una carpeta común; provisionar `.env` y `logitrack-local-secrets/security/{private,public}.pem` fuera de Git. Usar una BD de prueba, no reutilizar volúmenes productivos. Desde la carpeta común:

```powershell
docker compose --env-file .env -f shipment-service/compose.sprint3.release.yml config --quiet
docker compose --env-file .env -f shipment-service/compose.sprint3.release.yml pull
docker compose --env-file .env -f shipment-service/compose.sprint3.release.yml up -d --wait --wait-timeout 300
python shipment-service/scripts/sprint3_e2e.py
python shipment-service/scripts/verify-runtime.py
python shipment-service/scripts/sprint3_scale.py
node shipment-service/scripts/run-apidog.mjs
```

Durante la validación previa al merge se selecciona explícitamente `CONFIG_GIT_LABEL=fix/s3-config-contract`; después del merge se usa main. La configuración sigue viniendo de Git a través de Config Server. Las ejecuciones Maven unitarias deshabilitan Config/Eureka por propiedad de test para no depender de nombres DNS de Docker desde Windows; su integración se verifica separadamente por E2E.

Para modificar fuentes y reconstruir localmente, ejecutar `python shipment-service/scripts/build-manifest.py` y usar `compose.sprint3.yml build`. El entregable `compose.sprint3.release.yml` no contiene bloques build y utiliza las imágenes publicadas por digest. Los runners identifican el mismo proyecto Compose; las verificaciones inspeccionan los IDs realmente ejecutados.

La colección exportada conserva las doce variables del archivo confirmado por el usuario. Los runners no guardan tokens ni contraseñas en los reportes. `run-apidog.mjs` ejecuta los scripts prerequest/test originales mediante HTTP y documenta su runner: no se presenta como una ejecución de la aplicación de escritorio Apidog.

El preview de Swagger es una herramienta temporal en 127.0.0.1:8099 que solo admite rutas de documentación; no es parte de los nueve servicios ni una entrada de negocio. Apagarlo al terminar la revisión.

## Límites operativos declarados

Courier conserva ddl-auto:update solo para la demo académica según ADR-004; no se declara listo para producción. Falta una migración Courier revisada sobre backup para cualquier despliegue productivo. El TTL de acceso por defecto sigue siendo una hora, sin revocación inmediata de JWT ya emitidos. No se configuró TLS público ni se extrapolan mediciones locales a una plataforma productiva. Ninguno de esos puntos agrega funcionalidad de Sprint 4.

Fuentes oficiales consultadas: [multistage Docker](https://docs.docker.com/build/building/multi-stage/), [Temurin JDK/JRE](https://adoptium.net/temurin/releases/), [MySQL 8.4](https://dev.mysql.com/doc/relnotes/mysql/8.4/en/).
