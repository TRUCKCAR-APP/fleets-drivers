# fleets-drivers

## Quién puede recibir una carga nueva (`GET /api/v1/fleets-drivers/available`)

Un conductor entra en la lista de disponibles, que usan el motor de asignación y la torre, solo si cumple las cuatro condiciones:

1. **Estado operativo `ACTIVO`.** Registró su vehículo y no está en descanso ni inactivo.
2. **Cuenta verificada (`ACTIVE`).** La verifica un administrador en user-identity. Se recibe por `user.status.changed` y se guarda en el hash de Redis `driver_account_status`.
3. **Afiliación que permite cargas.** Llega por el evento `user.affiliation.changed` de user-identity y se guarda en el hash `driver_affiliation`:
   - `INDEPENDIENTE`: no recibe cargas. Usa la navegación, las rutas y las alertas.
   - `EMPRESA`: recibe cargas solo con la afiliación `ACTIVA`, es decir, cuando la transportadora lo aceptó.
   - Sin dato (cuentas anteriores a la afiliación): recibe cargas como antes.
4. **Sin una carga aceptada o en ruta.**

Con `?loadId=`, la lista además se filtra con los requisitos de esa carga: tipo de camión, capacidad, rechazos previos y propuestas vigentes.

**Campos de afiliación en las respuestas de flota** (`/active`, `/available`, `/drivers/{id}`):
- `driverType`;
- `companyName`;
- `affiliationStatus`;
- `loadEligible`.

La torre los usa para mostrar si un conductor es independiente o de qué empresa es.
