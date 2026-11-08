# ADR 005 — Compatibilidad del contrato de identidad

La lectura del contrato Sprint 2 confirmó registro `{nombre,email,password}` y login `{email,password}`, mientras el código y Apidog vigentes usan username. Se soportan ambos sin renombrar las variables existentes: `nombre` es alias de entrada de username; la respuesta incluye nombre y conserva username. Login acepta exclusivamente email o username, más password. No se infiere un email desde un username ni se permite ambigüedad al enviar ambos.

El bootstrap recibe ADMIN_EMAIL y ADMIN_PASSWORD; ADMIN_USERNAME es opcional con valor predeterminado admin. Si la identidad existe con rol ADMIN no modifica contraseña ni crea otra cuenta; si tiene otro rol, falla sin elevar privilegios. Una contraseña vacía desactiva explícitamente el alta automática. Login rechaza cuentas deshabilitadas y devuelve 401 por credenciales inválidas o identidad inexistente. La consulta administrativa de un usuario inexistente conserva 404.

No se incorpora confirmación de correo ni nuevos roles. Los cambios de compatibilidad se verifican en pruebas de API y no se declaran satisfechos solo por este documento.
