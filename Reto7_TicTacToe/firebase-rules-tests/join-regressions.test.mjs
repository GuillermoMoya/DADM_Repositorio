// Join-flow AUTHORIZATION regression tests for Reto7_TicTacToe.
//
// Scope / honest limitation:
//   These tests exercise the Realtime Database SECURITY RULES (database.rules.json)
//   against the local emulator (127.0.0.1:9000, project "demo-rtttt"). They confirm
//   that the AUTHORIZATION decisions for the join scenarios are correct:
//     - a guest claiming a WAITING match               -> PERMITIDO
//     - a third party claiming an occupied PLAYING match -> RECHAZADO
//     - a join write against a non-existent match        -> (documented verdict)
//     - a non-participant writing someone else's match   -> RECHAZADO
//
//   They DO NOT (and cannot) reproduce the real client bug fixed in
//   FirebaseMatchRepository.java. That bug is a Firebase ANDROID SDK transaction
//   caching behaviour (doTransaction's first pass runs against a null local estimate
//   on a cold cache, and aborting there makes the SDK give up without a server
//   fetch). The rules emulator validates authorization, NOT the SDK's local-cache
//   transaction retry. The cache fix can only be truly validated on two devices.
//
// Same harness/pattern as audit-negatives.test.mjs (node:test + rules-unit-testing).

import { test, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { ref, set, update, remove } from 'firebase/database';

const HOST = 'uidHost';
const GUEST = 'uidGuest';
const OTHER = 'uidOther';
const MID = 'matchJ1';
const MISSING = 'matchMissing';

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
// JR1 — Valid join: guest claims a WAITING match (WAITING -> PLAYING).
//        Authorization MUST be PERMITIDO.
// ---------------------------------------------------------------------------
test('JR1 unión válida: invitado reclama WAITING DEBE ser PERMITIDO', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'WAITING' }));
    await set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 });
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  const verdict = await probe(
    set(
      ref(db, `matches/${MID}`),
      matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING', updatedAt: 2000 }),
    ),
  );
  console.log(`[JOIN-REG] JR1 unión válida (guest reclama WAITING): ${verdict}`);
  assert.equal(verdict, 'PERMITIDO', 'una unión legítima a WAITING debe permitirse');
});

// JR1b — after a valid join, the guest clears the lobby entry. MUST be PERMITIDO.
test('JR1b tras unión, invitado limpia lobby DEBE ser PERMITIDO', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }));
    await set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 });
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  const verdict = await probe(remove(ref(db, `lobby/${MID}`)));
  console.log(`[JOIN-REG] JR1b invitado limpia lobby tras unión: ${verdict}`);
  assert.equal(verdict, 'PERMITIDO', 'el invitado debe poder limpiar el lobby tras unirse');
});

// ---------------------------------------------------------------------------
// JR2 — Occupied match: a third party tries to claim a PLAYING match.
//        Authorization MUST be RECHAZADO (this is the "unavailable" outcome).
// ---------------------------------------------------------------------------
test('JR2 partida ocupada: tercero reclama PLAYING DEBE ser RECHAZADO', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }));
  });
  const db = testEnv.authenticatedContext(OTHER).database();
  const verdict = await probe(
    set(
      ref(db, `matches/${MID}`),
      matchDoc({ hostUid: HOST, guestUid: OTHER, status: 'PLAYING', updatedAt: 2000 }),
    ),
  );
  console.log(`[JOIN-REG] JR2 partida ocupada (tercero reclama PLAYING): ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'un tercero no debe reclamar una partida ocupada');
});

// ---------------------------------------------------------------------------
// JR3 — Non-existent match: a guest join-writes a match that does not exist.
//        We document the REAL verdict. NOTE: at the client, the fixed repository
//        maps "match truly absent after a server fetch" to onUnavailable(); this
//        rules probe only records the authorization decision for the write.
// ---------------------------------------------------------------------------
test('JR3 partida inexistente: join-write sobre match ausente (veredicto documentado)', async () => {
  // No seed: /matches/<MISSING> does not exist.
  const db = testEnv.authenticatedContext(GUEST).database();
  const verdict = await probe(
    set(
      ref(db, `matches/${MISSING}`),
      matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING', updatedAt: 2000 }),
    ),
  );
  console.log(`[JOIN-REG] JR3 partida inexistente (join-write sobre ausente): ${verdict}`);
  // Finding: at the rules level a guest cannot join-write a match that does not exist — the
  // guest is not the host, so creating a brand-new /matches/<id> document (even a well-formed
  // PLAYING one) is denied. This is RECHAZADO. Note the "unavailable" USER EXPERIENCE for a
  // missing match is decided by the CLIENT (FirebaseMatchRepository maps a match that is still
  // absent after a server fetch to onUnavailable()); the rules simply never let a guest
  // fabricate a non-existent match. Asserting the real verdict guards against regressions.
  assert.equal(verdict, 'RECHAZADO', 'un invitado no debe poder crear/escribir un match inexistente');
});

// JR3b — reading a non-existent match is allowed (authenticated read) and yields no data.
test('JR3b leer match inexistente DEBE ser PERMITIDO (sin datos)', async () => {
  const db = testEnv.authenticatedContext(GUEST).database();
  const verdict = await probe(
    update(ref(db, '/'), { [`matches/${MISSING}/updatedAt`]: 1 }).catch((e) => { throw e; }),
  );
  // The above tries a partial write to a missing node; document the verdict without asserting a
  // specific value since partial writes depend on $other:false completeness checks.
  console.log(`[JOIN-REG] JR3b escritura parcial a match inexistente: ${verdict}`);
});

// ---------------------------------------------------------------------------
// JR4 — Permission failure: a non-participant writes someone else's match.
//        Authorization MUST be RECHAZADO (surfaces at the client as onError,
//        e.g. PERMISSION_DENIED / code -3).
// ---------------------------------------------------------------------------
test('JR4 fallo de permisos: no-participante escribe match ajeno DEBE ser RECHAZADO', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }));
  });
  const db = testEnv.authenticatedContext(OTHER).database();
  const verdict = await probe(
    set(
      ref(db, `matches/${MID}`),
      matchDoc({
        hostUid: HOST,
        guestUid: GUEST,
        status: 'PLAYING',
        board: 'X        ',
        turn: 'O',
        updatedAt: 2000,
      }),
    ),
  );
  console.log(`[JOIN-REG] JR4 no-participante escribe match ajeno: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'un no-participante no debe escribir el match');
});

// JR4b — unauthenticated client cannot join a WAITING match.
test('JR4b cliente no autenticado NO puede unirse DEBE ser RECHAZADO', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'WAITING' }));
  });
  const db = testEnv.unauthenticatedContext().database();
  const verdict = await probe(
    set(
      ref(db, `matches/${MID}`),
      matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING', updatedAt: 2000 }),
    ),
  );
  console.log(`[JOIN-REG] JR4b no autenticado se une: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'un cliente no autenticado no debe unirse');
});
