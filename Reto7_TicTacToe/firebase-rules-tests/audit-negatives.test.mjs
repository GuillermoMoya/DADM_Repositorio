// Negative / audit security-rules tests for Reto7_TicTacToe (READ-ONLY AUDIT).
//
// Purpose: empirically CONFIRM the CURRENT behaviour of database.rules.json for
// six authorization/deletion risks. This file documents reality: some dangerous
// writes may be PERMITIDO (allowed) — that is a FINDING, not a bug to "fix" here.
//
// Same harness/pattern as rules.test.mjs: node:test + @firebase/rules-unit-testing
// against the local RTDB emulator (127.0.0.1:9000), project id "demo-rtttt".
//
// Each case probes the real verdict and logs `[AUDIT] <name>: PERMITIDO|RECHAZADO`.
// For cases that MUST be blocked for security, a hard assert.equal(...,'RECHAZADO')
// (or 'PERMITIDO' for the legitimate flows) makes the runner fail on regression.
// Exploratory cases only log — documenting "PERMITIDO" is itself the goal.

import { test, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from '@firebase/rules-unit-testing';
import { ref, get, set, update, remove } from 'firebase/database';

const HOST = 'uidHost';
const GUEST = 'uidGuest';
const OTHER = 'uidOther';
const MID = 'match1';
const MID2 = 'match2';

const EMPTY_BOARD = '         '; // nine spaces, length 9

let testEnv;

/** Builds a complete, valid /matches/<id> document; overrides win. */
function matchDoc(overrides = {}) {
  return {
    hostUid: HOST,
    board: EMPTY_BOARD,
    turn: 'X',
    status: 'WAITING',
    winner: 0,
    createdAt: 1000,
    updatedAt: 1000,
    ...overrides,
  };
}

/**
 * Executes a write/read promise and returns the REAL verdict without throwing:
 *   'PERMITIDO'  if the operation resolves (rules allowed it)
 *   'RECHAZADO'  if the operation rejects (rules denied it)
 */
async function probe(promise) {
  try {
    await promise;
    return 'PERMITIDO';
  } catch (e) {
    return 'RECHAZADO';
  }
}

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: 'demo-rtttt',
    database: {
      rules: readFileSync('../database.rules.json', 'utf8'),
      host: '127.0.0.1',
      port: 9000,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  await testEnv.clearDatabase();
});

/** Pre-seeds state ignoring rules (admin context). */
async function seed(fn) {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await fn(ctx.database());
  });
}

// ---------------------------------------------------------------------------
// R1 — Lobby mutation by the host (exploratory).
// ---------------------------------------------------------------------------

// R1a: host mutates lobby hostUid to another uid -> now RECHAZADO (immutable).
test('R1a host muta lobby hostUid -> OTHER DEBE ser RECHAZADO', async () => {
  await seed(async (db) => {
    await set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 });
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'WAITING' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(
    set(ref(db, `lobby/${MID}`), { hostUid: OTHER, createdAt: 1000 }),
  );
  console.log(`[AUDIT] R1a host muta lobby hostUid: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'hostUid del lobby debe ser inmutable');
});

// R1b: host mutates lobby createdAt -> now RECHAZADO (immutable).
test('R1b host muta lobby createdAt DEBE ser RECHAZADO', async () => {
  await seed(async (db) => {
    await set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 });
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'WAITING' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(
    set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 9999 }),
  );
  console.log(`[AUDIT] R1b host muta lobby createdAt: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'createdAt del lobby debe ser inmutable');
});

// ---------------------------------------------------------------------------
// R2 — Whole-match deletion (exploratory + one expected-allowed flow).
// ---------------------------------------------------------------------------

// R2a: host deletes an entire PLAYING match -> now RECHAZADO (delete guard).
test('R2a host borra match PLAYING entero DEBE ser RECHAZADO', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(remove(ref(db, `matches/${MID}`)));
  console.log(`[AUDIT] R2a host borra match PLAYING: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'no se debe borrar un match PLAYING');
});

// R2b: guest deletes an entire PLAYING match -> now RECHAZADO (delete guard).
test('R2b guest borra match PLAYING entero DEBE ser RECHAZADO', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }));
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  const verdict = await probe(remove(ref(db, `matches/${MID}`)));
  console.log(`[AUDIT] R2b guest borra match PLAYING: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'el invitado no debe borrar un match PLAYING');
});

// R2c: host deletes a WAITING match (expected legitimate flow -> PERMITIDO).
test('R2c host borra match WAITING DEBE ser PERMITIDO', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'WAITING' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(remove(ref(db, `matches/${MID}`)));
  console.log(`[AUDIT] R2c host borra match WAITING: ${verdict}`);
  assert.equal(verdict, 'PERMITIDO', 'el host debe poder borrar una WAITING huérfana');
});

// ---------------------------------------------------------------------------
// R3 — Arbitrary (but well-formed) game-state writes by a participant.
// Rules do NOT validate turn ownership / win legitimacy -> suspected PERMITIDO.
// ---------------------------------------------------------------------------

// R3a: participant (guest) writes arbitrary board/turn/winner out of turn.
test('R3a participante escribe estado arbitrario fuera de turno (exploratorio)', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING', turn: 'X' }));
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  const verdict = await probe(
    set(
      ref(db, `matches/${MID}`),
      matchDoc({
        hostUid: HOST,
        guestUid: GUEST,
        status: 'PLAYING',
        board: 'XOXOXOXOX',
        turn: 'X',
        winner: 2,
        updatedAt: 2000,
      }),
    ),
  );
  console.log(`[AUDIT] R3a participante escribe estado arbitrario: ${verdict}`);
  // Límite conocido: las reglas no validan turno/legitimidad -> sigue PERMITIDO.
  assert.equal(verdict, 'PERMITIDO', 'límite conocido: escritura de estado arbitrario sigue permitida');
});

// R3b: participant declares themselves the winner.
test('R3b participante se declara ganador (exploratorio)', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING', turn: 'X' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(
    set(
      ref(db, `matches/${MID}`),
      matchDoc({
        hostUid: HOST,
        guestUid: GUEST,
        status: 'FINISHED',
        board: 'XXX      ', // 'X','X','X' + 6 spaces, length 9
        turn: 'X',
        winner: 2,
        updatedAt: 2000,
      }),
    ),
  );
  console.log(`[AUDIT] R3b participante se declara ganador: ${verdict}`);
  // Límite conocido: las reglas no validan la legitimidad del ganador -> sigue PERMITIDO.
  assert.equal(verdict, 'PERMITIDO', 'límite conocido: declararse ganador sigue permitido');
});

// ---------------------------------------------------------------------------
// R4 — Cases that MUST be blocked (hard asserts).
// ---------------------------------------------------------------------------

// R4a: a participant writes presence for another uid -> MUST be RECHAZADO.
test('R4a presencia ajena DEBE ser RECHAZADA', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }));
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  const verdict = await probe(set(ref(db, `matches/${MID}/presence/${HOST}`), true));
  console.log(`[AUDIT] R4a presencia ajena: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'presencia de otro uid NO debe permitirse');
});

// R4b: a stranger claims an already-occupied match -> MUST be RECHAZADO.
test('R4b reclamar partida ocupada DEBE ser RECHAZADO', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }));
  });
  const db = testEnv.authenticatedContext(OTHER).database();
  const verdict = await probe(
    set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: OTHER, status: 'PLAYING', updatedAt: 2000 })),
  );
  console.log(`[AUDIT] R4b reclamar partida ocupada: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'un tercero NO debe reclamar partida ocupada');
});

// R4c: host changes guestUid -> MUST be RECHAZADO (guestUid immutable).
test('R4c cambiar guestUid DEBE ser RECHAZADO', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(
    set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: OTHER, status: 'PLAYING', updatedAt: 2000 })),
  );
  console.log(`[AUDIT] R4c cambiar guestUid: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'guestUid NO debe ser mutable');
});

// R4d: a stranger alters/deletes another match's lobby entry -> MUST be RECHAZADO.
test('R4d alterar/borrar lobby de otra partida DEBE ser RECHAZADO', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID2}`), matchDoc({ hostUid: OTHER, status: 'WAITING' }));
    await set(ref(db, `lobby/${MID2}`), { hostUid: OTHER, createdAt: 1000 });
  });
  const db = testEnv.authenticatedContext(HOST).database();

  const verdictSet = await probe(
    set(ref(db, `lobby/${MID2}`), { hostUid: HOST, createdAt: 1000 }),
  );
  console.log(`[AUDIT] R4d alterar lobby ajeno (set): ${verdictSet}`);
  assert.equal(verdictSet, 'RECHAZADO', 'no debe alterar lobby de otra partida');

  const verdictRemove = await probe(remove(ref(db, `lobby/${MID2}`)));
  console.log(`[AUDIT] R4d borrar lobby ajeno (remove): ${verdictRemove}`);
  assert.equal(verdictRemove, 'RECHAZADO', 'no debe borrar lobby de otra partida');
});

// ---------------------------------------------------------------------------
// R5 — Legitimate flows that MUST be allowed.
// ---------------------------------------------------------------------------

// R5a: create-match multi-location update -> MUST be PERMITIDO.
test('R5a createMatch multiubicación DEBE ser PERMITIDO', async () => {
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(
    update(ref(db, '/'), {
      ['matches/' + MID]: matchDoc({ hostUid: HOST }),
      ['lobby/' + MID]: { hostUid: HOST, createdAt: 1000 },
    }),
  );
  console.log(`[AUDIT] R5a createMatch multiubicación: ${verdict}`);
  assert.equal(verdict, 'PERMITIDO', 'creación legítima debe permitirse');
});

// R5b: guest join rewrite -> MUST be PERMITIDO.
test('R5b join rewrite DEBE ser PERMITIDO', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'WAITING' }));
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  const verdict = await probe(
    set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING', updatedAt: 2000 })),
  );
  console.log(`[AUDIT] R5b join rewrite: ${verdict}`);
  assert.equal(verdict, 'PERMITIDO', 'unión legítima debe permitirse');
});

// R5c: legitimate move rewrite -> MUST be PERMITIDO.
test('R5c move rewrite legítimo DEBE ser PERMITIDO', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING', turn: 'X' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(
    set(
      ref(db, `matches/${MID}`),
      matchDoc({
        hostUid: HOST,
        guestUid: GUEST,
        status: 'PLAYING',
        board: 'X        ', // 'X' + 8 spaces, length 9
        turn: 'O',
        winner: 0,
        updatedAt: 2000,
      }),
    ),
  );
  console.log(`[AUDIT] R5c move rewrite legítimo: ${verdict}`);
  assert.equal(verdict, 'PERMITIDO', 'movimiento legítimo debe permitirse');
});

// R5d: leave -> ABANDONED (expected PERMITIDO; logged only).
test('R5d leave->ABANDONED (esperado PERMITIDO)', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(
    set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'ABANDONED', updatedAt: 2000 })),
  );
  console.log(`[AUDIT] R5d leave->ABANDONED: ${verdict}`);
});

// ---------------------------------------------------------------------------
// R6 — Unexpected fields ($other:false) and subfield-deletion bypass.
// ---------------------------------------------------------------------------

// R6a: unexpected field in match -> MUST be RECHAZADO by $other:false.
test('R6a campo inesperado en match DEBE ser RECHAZADO', async () => {
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(
    set(ref(db, `matches/${MID}`), { ...matchDoc({ hostUid: HOST }), evil: 'x' }),
  );
  console.log(`[AUDIT] R6a campo inesperado en match: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'campo desconocido en match no debe permitirse');
});

// R6b: unexpected field in lobby -> MUST be RECHAZADO.
test('R6b campo inesperado en lobby DEBE ser RECHAZADO', async () => {
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(
    set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000, evil: 'x' }),
  );
  console.log(`[AUDIT] R6b campo inesperado en lobby: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'campo desconocido en lobby no debe permitirse');
});

// R6c: delete a required subfield (guestUid) -> exploratory / critical.
test('R6c borrado de subcampo guestUid (exploratorio/crítico)', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(remove(ref(db, `matches/${MID}/guestUid`)));
  console.log(`[AUDIT] R6c borrado de subcampo guestUid: ${verdict}`);
});

// R6d: delete a required subfield (board) -> exploratory.
test('R6d borrado de subcampo board (exploratorio)', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(remove(ref(db, `matches/${MID}/board`)));
  console.log(`[AUDIT] R6d borrado de subcampo board: ${verdict}`);
});

// ---------------------------------------------------------------------------
// NR — New regression/hardening cases for the delete-guard + lobby immutability.
// ---------------------------------------------------------------------------

// NR1: host cannot delete a PLAYING match.
test('NR1 host NO puede borrar match PLAYING', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(remove(ref(db, `matches/${MID}`)));
  console.log(`[AUDIT] NR1 host borra PLAYING: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'host no debe borrar match PLAYING');
});

// NR2: guest cannot delete a PLAYING match.
test('NR2 guest NO puede borrar match PLAYING', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }));
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  const verdict = await probe(remove(ref(db, `matches/${MID}`)));
  console.log(`[AUDIT] NR2 guest borra PLAYING: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'guest no debe borrar match PLAYING');
});

// NR3: host can delete a WAITING match.
test('NR3 host SÍ puede borrar match WAITING', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'WAITING' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(remove(ref(db, `matches/${MID}`)));
  console.log(`[AUDIT] NR3 host borra WAITING: ${verdict}`);
  assert.equal(verdict, 'PERMITIDO', 'host debe poder borrar match WAITING');
});

// NR4: host cannot delete a FINISHED match.
test('NR4 host NO puede borrar match FINISHED', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'FINISHED', winner: 2 }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(remove(ref(db, `matches/${MID}`)));
  console.log(`[AUDIT] NR4 host borra FINISHED: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'host no debe borrar match FINISHED');
});

// NR5: guest cannot delete a WAITING match (no guest yet).
test('NR5 guest NO puede borrar match WAITING', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'WAITING' }));
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  const verdict = await probe(remove(ref(db, `matches/${MID}`)));
  console.log(`[AUDIT] NR5 guest borra WAITING: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'guest no debe borrar match WAITING');
});

// NR6: host cannot delete an ABANDONED match (reinforces NR4).
test('NR6 host NO puede borrar match ABANDONED', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'ABANDONED' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(remove(ref(db, `matches/${MID}`)));
  console.log(`[AUDIT] NR6 host borra ABANDONED: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'host no debe borrar match ABANDONED');
});

// NR7: host cannot change lobby hostUid.
test('NR7 host NO cambia lobby hostUid', async () => {
  await seed(async (db) => {
    await set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 });
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'WAITING' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(
    set(ref(db, `lobby/${MID}`), { hostUid: OTHER, createdAt: 1000 }),
  );
  console.log(`[AUDIT] NR7 host cambia lobby hostUid: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'hostUid del lobby debe ser inmutable');
});

// NR8: host cannot change lobby createdAt.
test('NR8 host NO cambia lobby createdAt', async () => {
  await seed(async (db) => {
    await set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 });
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'WAITING' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(
    set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 9999 }),
  );
  console.log(`[AUDIT] NR8 host cambia lobby createdAt: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'createdAt del lobby debe ser inmutable');
});

// NR9: creating a lobby entry (initial set) is allowed.
test('NR9 crear entrada de lobby (set inicial) SÍ permitido', async () => {
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(
    set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 }),
  );
  console.log(`[AUDIT] NR9 crear lobby inicial: ${verdict}`);
  assert.equal(verdict, 'PERMITIDO', 'creación inicial de lobby debe permitirse');
});

// NR10: guest can delete the lobby entry after joining (cross-delete non-regression).
test('NR10 borrado legítimo de lobby por invitado tras unirse', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }));
    await set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 });
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  const verdict = await probe(remove(ref(db, `lobby/${MID}`)));
  console.log(`[AUDIT] NR10 invitado borra lobby tras unirse: ${verdict}`);
  assert.equal(verdict, 'PERMITIDO', 'el invitado debe poder limpiar el lobby tras unirse');
});

// NR11: host can clean up its own lobby entry.
test('NR11 limpieza de lobby por host', async () => {
  await seed(async (db) => {
    await set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 });
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'WAITING' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(remove(ref(db, `lobby/${MID}`)));
  console.log(`[AUDIT] NR11 host limpia lobby: ${verdict}`);
  assert.equal(verdict, 'PERMITIDO', 'el host debe poder limpiar su lobby');
});

// NR12: own presence allowed, foreign presence rejected (reaffirm).
test('NR12 presencia propia permitida y ajena rechazada', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }));
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  const own = await probe(set(ref(db, `matches/${MID}/presence/${GUEST}`), true));
  console.log(`[AUDIT] NR12 presencia propia: ${own}`);
  assert.equal(own, 'PERMITIDO', 'presencia propia debe permitirse');
  const foreign = await probe(set(ref(db, `matches/${MID}/presence/${HOST}`), true));
  console.log(`[AUDIT] NR12 presencia ajena: ${foreign}`);
  assert.equal(foreign, 'RECHAZADO', 'presencia ajena debe rechazarse');
});

// NR13: move rewrite still allowed after the .write change.
test('NR13 move rewrite sigue permitido tras el cambio de .write', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING', turn: 'X' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(
    set(
      ref(db, `matches/${MID}`),
      matchDoc({
        hostUid: HOST,
        guestUid: GUEST,
        status: 'PLAYING',
        board: 'X        ', // 'X' + 8 spaces, length 9
        turn: 'O',
        winner: 0,
        updatedAt: 2000,
      }),
    ),
  );
  console.log(`[AUDIT] NR13 move rewrite: ${verdict}`);
  assert.equal(verdict, 'PERMITIDO', 'movimiento legítimo debe seguir permitido');
});

// NR14: join rewrite still allowed.
test('NR14 join rewrite sigue permitido', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'WAITING' }));
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  const verdict = await probe(
    set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING', updatedAt: 2000 })),
  );
  console.log(`[AUDIT] NR14 join rewrite: ${verdict}`);
  assert.equal(verdict, 'PERMITIDO', 'unión legítima debe seguir permitida');
});

// NR15: abandonment (PLAYING -> ABANDONED) still allowed.
test('NR15 abandono (PLAYING->ABANDONED) sigue permitido', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }));
  });
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(
    set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'ABANDONED', updatedAt: 2000 })),
  );
  console.log(`[AUDIT] NR15 abandono: ${verdict}`);
  assert.equal(verdict, 'PERMITIDO', 'abandono legítimo debe seguir permitido');
});

// NR16: createMatch multi-location update still allowed.
test('NR16 createMatch multiubicación sigue permitido', async () => {
  const db = testEnv.authenticatedContext(HOST).database();
  const verdict = await probe(
    update(ref(db, '/'), {
      ['matches/' + MID]: matchDoc({ hostUid: HOST }),
      ['lobby/' + MID]: { hostUid: HOST, createdAt: 1000 },
    }),
  );
  console.log(`[AUDIT] NR16 createMatch multiubicación: ${verdict}`);
  assert.equal(verdict, 'PERMITIDO', 'creación multiubicación debe seguir permitida');
});
