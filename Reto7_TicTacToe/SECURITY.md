# Seguridad del Modo_Online (Reto 7 — Triqui Online)

Este documento describe las **Reglas_Seguridad** de la Realtime Database (RTDB) usadas por el
Modo_Online, el procedimiento para pasar de las reglas de **modo de prueba** a las de
**producción**, y — de forma explícita — el **alcance** de lo que estas reglas sí y no pueden
hacer cumplir.

El ruleset de producción vive en [`database.rules.json`](./database.rules.json) en la raíz del
proyecto.

> **Requisitos cubiertos:** 1.2, 15.3, 22.1, 22.2, 22.3, 22.5, 22.6, 22.7.

---

## 1. Qué hacen cumplir las Reglas_Seguridad de producción

El archivo `database.rules.json` está **bloqueado por defecto** (`".read": false`,
`".write": false` en la raíz) y solo abre lo estrictamente necesario bajo `/lobby` y `/matches`.
La producción **NO** usa reglas abiertas ni de modo de prueba (Req 22.5).

Las reglas imponen lo que **sí** es expresable en reglas de RTDB:

- **Solo usuarios identificados** pueden leer o escribir (`auth != null`). Un usuario no
  autenticado es rechazado en toda lectura/escritura (Req 22.2).
- **Autoría de un match (`/matches/$matchId`):**
  - Si el match no existe, solo puede crearlo quien se declare como `hostUid` (`newData.hostUid === auth.uid`).
  - Si el match existe, solo pueden escribirlo su `hostUid` o su `guestUid`.
  - Un **tercero** puede escribir **únicamente** para reclamar el slot de Invitado y
    **solo cuando** el match está `WAITING` y el slot `guestUid` está vacío
    (`status === 'WAITING' && !guestUid.exists() && newData.guestUid === auth.uid`).
    Esto habilita `joinMatch` (Req 10.1) y, una vez fijado `guestUid`, **impide a un tercer
    jugador** escribir en una partida ya completa (Req 1.2, 15.3, 22.3).
- **Forma del dato (`.validate`):** cada escritura de un match debe tener
  `board` como string de **exactamente 9 caracteres**, y presencia/forma válida de
  `status` (uno de `WAITING`/`PLAYING`/`FINISHED`/`ABANDONED`), `turn` (`X`/`O`),
  `winner` (entero `0..3`, alineado con `TicTacToeGame.checkForWinner`) y `hostUid` (string).
  Las claves no reconocidas se rechazan (`"$other": { ".validate": false }`).
- **Lobby (`/lobby`):** lectura para cualquier usuario identificado (lobby global compartido,
  Req 7); escritura de una entrada solo por su `hostUid`.

### Endurecimientos adicionales de autorización

Sobre la base anterior, el ruleset incorpora tres restricciones adicionales de autorización:

- **Borrado de la entrada de Lobby por el Invitado:** además del Anfitrión, el Invitado puede
  eliminar `/lobby/<matchId>` únicamente si figura como `guestUid` del match correspondiente
  (`root.child('matches').child($matchId).child('guestUid') === auth.uid`) y el match ya no está
  en `WAITING`. Esto permite retirar del Lobby una partida recién unida sin autorizar el borrado
  de partidas ajenas. (Antes solo el Anfitrión podía borrar la entrada, lo que impedía que el
  Invitado la retirara tras unirse.)
- **Inmutabilidad de identidades:** `hostUid` no puede cambiar tras crearse la partida y
  `guestUid` solo puede pasar de ausente a un uid una vez; después queda fijo (no se puede
  reemplazar ni vaciar).
- **Presencia acotada al propietario:** una entrada `presence/<uid>` solo puede ser escrita por
  ese mismo `uid` (`$uid === auth.uid`); el handler `onDisconnect` que la pone en `false` corre
  bajo el mismo uid y sigue funcionando.

> **Estado:** estos endurecimientos están expresados en las reglas pero **NO** han sido
> verificados en runtime (requieren Firebase Emulator Suite o un proyecto real); su validación
> queda pendiente.

---

## 2. Alcance de seguridad — límites de las reglas (corrección de sobre-afirmación)

Es importante **no sobre-afirmar** lo que ofrece la seguridad del cliente ni las reglas de RTDB:

- Las **transacciones del cliente** (`runTransaction` en `joinMatch`/`submitMove`) proveen
  **control de concurrencia para clientes cooperantes**: ante dos escrituras concurrentes,
  una gana y la otra reintenta/aborta. Esto **NO constituye una frontera de seguridad**.
- Un **cliente manipulado o malicioso** puede intentar escrituras arbitrarias. Las reglas de
  RTDB restringen **quién** escribe (autoría por `uid`) y **la forma** del dato (`.validate`),
  pero **no** pueden:
  - **recomputar la legalidad completa del turno** (que la marca de quien escribe coincida con
    `turn`, que no se salte turnos, que no sobrescriba celdas ya ocupadas), ni
  - **recalcular el `winner`** a partir del tablero para verificar que el valor persistido es el
    correcto según `checkForWinner`.

### ¿Cuándo se requiere lógica del lado del servidor?

Se requiere **lógica autoritativa del lado del servidor** (p. ej. **Cloud Functions** de Firebase
o un backend equivalente) cuando **no se pueda confiar en el cliente** y se necesite imponer, de
forma inviolable:

1. La **legalidad de turno** (que el movimiento provenga del jugador cuyo turno es y respete el
   estado del tablero), y/o
2. El **recálculo autoritativo del `winner`** y de la transición de estado
   (`PLAYING → FINISHED`), independientemente de lo que afirme el cliente.

Mientras el proyecto confíe en clientes **cooperantes** (contexto académico de este reto), la
combinación de **transacciones + reglas de autorización/forma** cubre los requisitos definidos.
El endurecimiento frente a un cliente **adversario** queda **fuera del alcance actual** y
necesitaría la lógica de servidor descrita arriba.

---

## 3. Procedimiento: modo de prueba → producción (Req 22.6)

Durante el desarrollo, Firebase sugiere **reglas en modo de prueba** (lectura/escritura abiertas
con fecha de expiración). Son adecuadas **solo** para desarrollo local y **deben** reemplazarse
antes de la entrega/evaluación.

1. **Desarrollo (temporal):** las reglas en modo de prueba de la Consola son aceptables solo de
   forma temporal.
2. **Antes de la entrega/evaluación:** reemplazar el contenido de las reglas por el ruleset de
   producción de este repositorio. Dos vías:
   - **Consola de Firebase:** Realtime Database → **Rules** → pegar el contenido de
     [`database.rules.json`](./database.rules.json) → **Publish**.
   - **Firebase CLI:** apuntar `database.rules` a `database.rules.json` en `firebase.json` y
     ejecutar `firebase deploy --only database`.
3. **Verificar con el simulador de reglas** de la Consola:
   - un usuario **no autenticado** es rechazado en lectura y escritura;
   - un **tercer uid** no puede escribir en un match ya completo (`guestUid` fijado);
   - un **uid participante** (host o guest) sí puede escribir;
   - una escritura con `board` de longitud ≠ 9 es rechazada por `.validate`.
4. **Documentar** en el README/entrega el estado de las reglas desplegadas (producción, no modo
   de prueba).

---

## 4. Credenciales y configuración (Req 22.7)

- **No** se incluyen credenciales ni configuración real de Firebase en el repositorio ni en la
  especificación.
- `app/google-services.json` se provee **fuera de banda** (out-of-band) y está **gitignored**;
  nunca se versiona con claves reales.
- Este documento y `database.rules.json` **no** contienen secretos: `database.rules.json` solo
  describe políticas de acceso, no claves.
