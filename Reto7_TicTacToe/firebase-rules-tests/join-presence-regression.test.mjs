// Regression tests for the ONLINE join bug: runJoinTransaction used
// mutableData.setValue(<whole document>), which re-sent presence/<host> under the
// guest's auth.uid. The presence/$uid rule (".validate": "$uid === auth.uid") then
// rejected it as Permission denied (-3). The fix updates ONLY the fields that change
// on join (guestUid/status/turn/updatedAt), leaving the presence subtree intact.
//
// These tests emulate the REAL corrected write — a PARTIAL multi-field update of the
// match node (not a whole-document set) — which is exactly what the fixed transaction
// does with mutableData.child(x).setValue(y). They also reproduce the old bug (JP3)
// to document why the whole-document set failed.
//
// Same harness/pattern as audit-negatives.test.mjs: node:test + @firebase/rules-unit-testing
// against the local RTDB emulator (127.0.0.1:9000), project id "demo-rtttt".
//
// HONESTY: the rules emulator validates AUTHORIZATION of the partial update only; it
// does NOT run the Android SDK transaction. JP1-JP6 confirm the partial write the code
// now performs is accepted by the rules and the full set was rejected. The real
// transactional flow between two phones is only verifiable on-device.

import { test, before, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import {
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import { ref, get, set, update } from 'firebase/database';

const HOST = 'uidHost';
const GUEST = 'uidGuest';
const OTHER = 'uidOther';
const MID = 'matchP1';

const EMPTY_BOARD = '         '; // nine spaces, length 9

let testEnv;

/** Builds a complete, valid /matches/<id> document including presence; overrides win. */
function matchDoc(overrides = {}) {
  return {
    hostUid: HOST,
    board: EMPTY_BOARD,
    turn: 'X',
    status: 'WAITING',
    winner: 0,
    createdAt: 1000,
    updatedAt: 1000,
    presence: { [HOST]: true },
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
// JP1 — Legitimate join as a PARTIAL update, preserving the host's presence.
// This mirrors the corrected transaction (child(x).setValue(y)) exactly.
// ---------------------------------------------------------------------------
test('JP1 unión legítima con presence del host (update parcial) DEBE ser PERMITIDO', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'WAITING', presence: { [HOST]: true } }));
    await set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 });
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  const verdict = await probe(
    update(ref(db, `matches/${MID}`), {
      guestUid: GUEST,
      status: 'PLAYING',
      turn: 'X',
      updatedAt: 2000,
    }),
  );
  console.log(`[JP1] unión legítima update parcial: ${verdict}`);
  assert.equal(verdict, 'PERMITIDO', 'el update parcial de unión debe permitirse sin tocar presence');
});

// ---------------------------------------------------------------------------
// JP2 — After JP1, the resulting state is correct and presence is preserved.
// ---------------------------------------------------------------------------
test('JP2 tras JP1 el estado quedó correcto (presence del host conservada)', async () => {
  // Leave the post-join state using rules-disabled so we can read and verify it.
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'WAITING', presence: { [HOST]: true } }));
    await set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 });
    await update(ref(db, `matches/${MID}`), {
      guestUid: GUEST,
      status: 'PLAYING',
      turn: 'X',
      updatedAt: 2000,
    });
  });

  const db = testEnv.authenticatedContext(HOST).database();
  const snap = await get(ref(db, `matches/${MID}`));
  const val = snap.val();
  console.log(`[JP2] estado post-unión: ${JSON.stringify(val)}`);
  assert.equal(val.guestUid, GUEST, 'guestUid debe ser el invitado');
  assert.equal(val.status, 'PLAYING', 'status debe ser PLAYING');
  assert.equal(val.turn, 'X', 'turn debe ser X');
  assert.equal(val.presence && val.presence[HOST], true, 'presence del host debe conservarse');
  assert.equal(val.board, EMPTY_BOARD, 'board debe quedar sin cambios (9 espacios)');
});

// ---------------------------------------------------------------------------
// JP3 — Regression of the BUG: whole-document set including host's presence
// by the guest MUST be rejected. This is what the old setValue(next) did.
// ---------------------------------------------------------------------------
test('JP3 set del documento COMPLETO con presence del host DEBE ser RECHAZADO (bug original)', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'WAITING', presence: { [HOST]: true } }));
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  const verdict = await probe(
    set(
      ref(db, `matches/${MID}`),
      matchDoc({
        hostUid: HOST,
        guestUid: GUEST,
        status: 'PLAYING',
        turn: 'X',
        updatedAt: 2000,
        presence: { [HOST]: true },
      }),
    ),
  );
  console.log(`[JP3] set documento completo con presence host: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'reescribir el documento completo con presence/<host> como invitado debe rechazarse');
});

// ---------------------------------------------------------------------------
// JP4 — The guest cannot write the host's presence directly.
// ---------------------------------------------------------------------------
test('JP4 invitado NO puede escribir presence del host directamente', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'WAITING', presence: { [HOST]: true } }));
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  const verdict = await probe(set(ref(db, `matches/${MID}/presence/${HOST}`), true));
  console.log(`[JP4] invitado escribe presence del host: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'presencia del host escrita por el invitado debe rechazarse');
});

// ---------------------------------------------------------------------------
// JP5 — The guest's OWN presence is allowed.
// ---------------------------------------------------------------------------
test('JP5 presencia propia del invitado SÍ permitida', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING', presence: { [HOST]: true } }));
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  const verdict = await probe(set(ref(db, `matches/${MID}/presence/${GUEST}`), true));
  console.log(`[JP5] invitado escribe su propia presencia: ${verdict}`);
  assert.equal(verdict, 'PERMITIDO', 'presencia propia del invitado debe permitirse');
});

// ---------------------------------------------------------------------------
// JP6 — A non-participant cannot perform the join update on an occupied match.
// ---------------------------------------------------------------------------
test('JP6 no-participante NO puede hacer el update de unión (partida ocupada)', async () => {
  await seed(async (db) => {
    await set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING', presence: { [HOST]: true } }));
  });
  const db = testEnv.authenticatedContext(OTHER).database();
  const verdict = await probe(
    update(ref(db, `matches/${MID}`), {
      guestUid: OTHER,
      status: 'PLAYING',
      turn: 'X',
      updatedAt: 3000,
    }),
  );
  console.log(`[JP6] no-participante hace update de unión en partida ocupada: ${verdict}`);
  assert.equal(verdict, 'RECHAZADO', 'un tercero no debe poder reclamar una partida ocupada (guestUid inmutable)');
});
