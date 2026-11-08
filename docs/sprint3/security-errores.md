# Unificación final de errores de Security

La revisión final detectó que el filtro JWT devolvía el formato común, pero los errores heredados de login y validación usaban `message`, `path` y un timestamp sin zona. La corrección puntual del commit Security `57729b4` conserva los códigos HTTP y normaliza el JSON a `status`, `error`, `mensaje`, `timestamp` UTC; la ruta interna no se serializa.

Se verificaron seis tests de Security y 36 comprobaciones directas de las APIs, incluido login por email, credenciales inválidas, entrada inválida y los negativos JWT de cada Resource Server. Apidog mantiene siete requests y 16 aserciones aprobadas. Los reportes se generan desde esas ejecuciones, sin tokens ni contraseñas.

La nueva imagen local es `logitrack/security-service:s3-d63f2811f1b3`. Su publicación e integración adicional se acreditan mediante el PR y `published-images.json`; este documento no los presume. La matriz no puede regenerarse como cierre técnico si el digest de fuentes ejecutado no coincide con una imagen publicada y descargada.
