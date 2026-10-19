// Local Firebase Realtime Database security-rules tests for Reto7_TicTacToe.
// Runner: node:test (native). Harness: @firebase/rules-unit-testing against the
// local RTDB emulator (127.0.0.1:9000). Project id is the fictitious "demo-rtttt".
//
// Data model mirrors MatchState / MatchSummary and database.rules.json:
//   /matches/<id> = { hostUid, guestUid?, board(9 chars), turn('X'|'O'),
//                     status(WAITING|PLAYING|FINISHED|ABANDONED), winner(0..3),
//                     createdAt, updatedAt, presence { <uid>: boolean } }
//   /lobby/<id>   = { hostUid, createdAt }

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

// 1. Unauthenticated cannot read matches nor write lobby.
test('no autenticado no puede leer ni escribir', async () => {
  const db = testEnv.unauthenticatedContext().database();
  await assertFails(get(ref(db, `matches/${MID}`)));
  await assertFails(
    set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 }),
  );
});

// 2. Legitimate match creation by host.
test('creación legítima de match por host', async () => {
  const db = testEnv.authenticatedContext(HOST).database();
  await assertSucceeds(
    set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST })),
  );
});

// 3. Match creation with hostUid != auth is rejected.
test('creación de match con hostUid distinto al auth es rechazada', async () => {
  const db = testEnv.authenticatedContext(HOST).database();
  await assertFails(
    set(ref(db, `matches/${MID}`), matchDoc({ hostUid: OTHER })),
  );
});

// 4. Legitimate lobby entry creation by host.
test('creación legítima de entrada de lobby por host', async () => {
  const db = testEnv.authenticatedContext(HOST).database();
  await assertSucceeds(
    set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 }),
  );
});

// 5. Lobby rejects an entry whose hostUid != auth.
test('lobby rechaza entrada con hostUid != auth', async () => {
  const db = testEnv.authenticatedContext(OTHER).database();
  await assertFails(
    set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 }),
  );
});

// 6. Guest joins (third party claims guest while WAITING).
test('unión por invitado (tercero reclama guest en WAITING)', async () => {
  await seed(async (db) => {
    await set(
      ref(db, `matches/${MID}`),
      matchDoc({ hostUid: HOST, status: 'WAITING' }),
    );
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  await assertSucceeds(
    set(
      ref(db, `matches/${MID}`),
      matchDoc({
        hostUid: HOST,
        guestUid: GUEST,
        status: 'PLAYING',
        turn: 'X',
        updatedAt: 2000,
      }),
    ),
  );
});

// 7. A third party cannot join a match that is already full/started.
test('un tercero no puede unirse a match ya completo', async () => {
  await seed(async (db) => {
    await set(
      ref(db, `matches/${MID}`),
      matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }),
    );
  });
  const db = testEnv.authenticatedContext(OTHER).database();
  await assertFails(
    set(
      ref(db, `matches/${MID}`),
      matchDoc({
        hostUid: HOST,
        guestUid: OTHER,
        status: 'PLAYING',
        updatedAt: 2000,
      }),
    ),
  );
});

// 8. Guest deletes the lobby entry after joining.
test('borrado del lobby por el invitado tras unirse', async () => {
  await seed(async (db) => {
    await set(
      ref(db, `matches/${MID}`),
      matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }),
    );
    await set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 });
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  await assertSucceeds(remove(ref(db, `lobby/${MID}`)));
});

// 9. Guest deleting the lobby entry BEFORE joining (match still WAITING) is rejected.
test('borrado del lobby por el invitado ANTES de unirse (match aún WAITING) es rechazado', async () => {
  await seed(async (db) => {
    await set(
      ref(db, `matches/${MID}`),
      matchDoc({ hostUid: HOST, status: 'WAITING' }),
    );
    await set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 });
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  await assertFails(remove(ref(db, `lobby/${MID}`)));
});

// 10. A stranger cannot delete the lobby entry.
test('borrado del lobby por tercero ajeno es rechazado', async () => {
  await seed(async (db) => {
    await set(
      ref(db, `matches/${MID}`),
      matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }),
    );
    await set(ref(db, `lobby/${MID}`), { hostUid: HOST, createdAt: 1000 });
  });
  const db = testEnv.authenticatedContext(OTHER).database();
  await assertFails(remove(ref(db, `lobby/${MID}`)));
});

// 11. hostUid is immutable.
test('hostUid es inmutable', async () => {
  await seed(async (db) => {
    await set(
      ref(db, `matches/${MID}`),
      matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }),
    );
  });
  const db = testEnv.authenticatedContext(HOST).database();
  await assertFails(
    set(
      ref(db, `matches/${MID}`),
      matchDoc({
        hostUid: OTHER,
        guestUid: GUEST,
        status: 'PLAYING',
        updatedAt: 2000,
      }),
    ),
  );
});

// 12. guestUid is immutable once assigned.
test('guestUid es inmutable una vez asignado', async () => {
  await seed(async (db) => {
    await set(
      ref(db, `matches/${MID}`),
      matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }),
    );
  });
  const db = testEnv.authenticatedContext(HOST).database();
  // Change guestUid to another uid -> rejected.
  await assertFails(
    set(
      ref(db, `matches/${MID}`),
      matchDoc({
        hostUid: HOST,
        guestUid: OTHER,
        status: 'PLAYING',
        updatedAt: 2000,
      }),
    ),
  );
  // Remove guestUid (absent) -> rejected.
  await assertFails(
    set(
      ref(db, `matches/${MID}`),
      matchDoc({ hostUid: HOST, status: 'PLAYING', updatedAt: 2000 }),
    ),
  );
});

// 13. Legitimate rewrite preserving identities (a move).
test('reescritura legítima conservando identidades (movimiento)', async () => {
  await seed(async (db) => {
    await set(
      ref(db, `matches/${MID}`),
      matchDoc({
        hostUid: HOST,
        guestUid: GUEST,
        status: 'PLAYING',
        turn: 'X',
        board: EMPTY_BOARD,
      }),
    );
  });
  const db = testEnv.authenticatedContext(HOST).database();
  await assertSucceeds(
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
});

// 14. board validation: length != 9 is rejected.
test('validación de board: longitud != 9 es rechazada', async () => {
  const db = testEnv.authenticatedContext(HOST).database();
  await assertFails(
    set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, board: '        ' })), // 8 spaces
  );
});

// 15. invalid status is rejected.
test('validación de status inválido es rechazada', async () => {
  const db = testEnv.authenticatedContext(HOST).database();
  await assertFails(
    set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, status: 'FOO' })),
  );
});

// 16. winner out of range is rejected.
test('validación de winner fuera de rango es rechazada', async () => {
  const db = testEnv.authenticatedContext(HOST).database();
  await assertFails(
    set(ref(db, `matches/${MID}`), matchDoc({ hostUid: HOST, winner: 5 })),
  );
});

// 17. own presence allowed.
test('presencia propia permitida', async () => {
  await seed(async (db) => {
    await set(
      ref(db, `matches/${MID}`),
      matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }),
    );
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  await assertSucceeds(set(ref(db, `matches/${MID}/presence/${GUEST}`), true));
  await assertSucceeds(set(ref(db, `matches/${MID}/presence/${GUEST}`), false));
});

// 18. presence for another uid is rejected.
test('presencia de otro uid rechazada', async () => {
  await seed(async (db) => {
    await set(
      ref(db, `matches/${MID}`),
      matchDoc({ hostUid: HOST, guestUid: GUEST, status: 'PLAYING' }),
    );
  });
  const db = testEnv.authenticatedContext(GUEST).database();
  await assertFails(set(ref(db, `matches/${MID}/presence/${HOST}`), true));
});
