# BFF Web

Adaptador del canal Web. Recibe consultas autenticadas, llama a Account Service
y devuelve el contrato del canal: datos de cuenta, saldos y movimientos.

## Endpoints actuales

| Método / ruta | Permiso |
|---|---|
| GET /api/web/cuentas/{cuentaId} | web.read |
| GET /api/web/transacciones/{id} | web.read |
| GET /actuator/info | web.info |
| GET /actuator/health | Público, sin detalles internos |


El perfil cloud importa config-repo/bff-web-cloud.properties.