# Implementation Plan: orientation-and-state-persistence (Challenge 6)

## Overview

This plan follows the design's Section 14 "Implementation Order" as its backbone. Each top-level step is independently buildable and leaves the app working, extending the existing (and preserved) Challenge 5 architecture rather than redesigning it. The language is Java (existing Android `app` module, package `co.edu.unal.reto4_tictactoe`); the design specifies concrete Java/Android classes, so no implementation-language selection is required.

Testing is intentionally **minimal and manual** per the non-functional constraints (requirements §"Non-Functional" item 4). The only automated tests are a few FOCUSED, OPTIONAL JVM unit tests over the pure additions (`TicTacToeGame` full-board API and `TurnOrchestrator.applyHumanMove`/`playComputerMove`); every automated test sub-task is marked optional with `*`. Acceptance is driven by the 7 manual verification tests (design §12).

Each task cites the requirement number(s) it implements and names the exact classes/files/methods from the design.

## Tasks

- [x] 1. Add full-board serialization API to `TicTacToeGame`
  - In `app/src/main/java/.../TicTacToeGame.java` add `getBoardState():char[]` returning a **defensive copy** of all 9 cells in index order 0..8
  - Add `setBoardState(char[])` that replaces the internal board with a **defensive copy** of the caller's array
  - Guard `setBoardState`: if the argument is `null` or its length is not exactly 9, leave the internal board **unchanged** (no partial write)
  - Keep the existing single-cell `getBoardState(int)` overload and all game logic (`clearBoard`, `setMove`, `checkForWinner`, `getComputerMove`, difficulty) unchanged
  - _Requirements: 3.1, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

  - [x] 1.1 Write focused JVM unit test for the full-board API
    - Round-trip: `setBoardState(x)` then `getBoardState()` yields equal cells
    - Defensive-copy isolation: mutating the returned array or the caller's array does not change the model
    - Guard: `null` and non-9-length inputs leave the board unchanged
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [x] 2. Split the turn in `TurnOrchestrator` into two pure half-turns
  - In `app/src/main/java/.../TurnOrchestrator.java` add pure `applyHumanMove(Game, position, SoundCue):Result` that applies **only** the human move: on illegal/occupied cell return the ignored `Result`; otherwise play the `HUMAN` sound cue, re-check the winner, and set `enteredComputerTurn = (winner == 0)`
  - Add pure `playComputerMove(Game, SoundCue):Result` that applies **only** the computer move: `getComputerMove()` with a `-1` guard (return ignored `Result`), else `setMove(COMPUTER, move)`, play the `COMPUTER` sound cue, re-check the winner
  - `applyHumanMove(...)` and `playComputerMove(...)` are **extracted** from the existing turn logic currently inside `onMoveSelected` (reuse the exact sub-sequences already present there)
  - **Temporarily preserve** the existing `onMoveSelected(...)` behavior unchanged (no behavior change to existing callers yet) only until task 9 updates `MainActivity` to use the new half-turn methods
  - After task 9, there must be **no duplicated or obsolete turn-flow logic left behind**: reconcile/remove the now-unused combined orchestration path so the two half-turn methods are the single source of truth for turn flow in `TurnOrchestrator`
  - _Requirements: 5.1, 11.11_

  - [x] 2.1 Write focused JVM unit tests for the split half-turns (mirror existing `TurnOrchestrator` test style)
    - `applyHumanMove` applies only the human move and reports `enteredComputerTurn` true only when the game continues
    - `applyHumanMove` ignores an occupied/invalid cell
    - `playComputerMove` applies exactly one computer move (and ignores when no cell is available)
    - _Requirements: 5.1, 11.11_

- [x] 3. Add strings, menu changes, and the portrait scoreboard
  - In `app/src/main/res/values/strings.xml` add `reset_scores` and the score label/format strings used by `displayScores()`
  - In `app/src/main/res/menu/options_menu.xml` add a `reset_scores` item (`showAsAction="never"`) and **remove** the `quit` item; keep `new_game` and `ai_difficulty`
  - In `app/src/main/res/layout/activity_main.xml` (portrait) add the score `TextView`(s) with a shared id, keeping the rest of the portrait hierarchy (`R.id.toolbar`, `R.id.information`, `R.id.board_view`) unchanged
  - _Requirements: 2.1, 7.6, 9.5_

- [x] 4. Add score state and menu wiring in `MainActivity`
  - In `app/src/main/java/.../MainActivity.java` add int fields `mHumanWins`, `mComputerWins`, `mTies`
  - Add `displayScores()` as the single score-UI update point that formats the three ints into the score `TextView`(s)
  - In the winner-handling path, after `result.wasApplied()`, increment by `result.winner()` (2 → `mHumanWins`, 3 → `mComputerWins`, 1 → `mTies`; 0 does nothing) then call `displayScores()`
  - Add `resetScores()` that zeroes all three, calls `displayScores()`, and persists immediately (persistence body added in task 5)
  - Wire menu `reset_scores` → `resetScores()`; remove the `R.id.quit` branch from `onOptionsItemSelected` (`showQuitDialog` may remain as dead code); keep `new_game` and `ai_difficulty` functional
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 9.1, 9.2, 9.5, 9.6_

- [x] 5. Persist and restore scores via SharedPreferences
  - In `MainActivity` read scores from `getSharedPreferences("ttt_prefs", MODE_PRIVATE)` using keys `PREF_HUMAN_WINS`/`PREF_COMPUTER_WINS`/`PREF_TIES` (default 0) in `onCreate`, then call `displayScores()`
  - Add `onStop()` that writes the three current score ints to `"ttt_prefs"`
  - In `resetScores()` write the three zeroed ints immediately (do not wait for `onStop`); leave persisted difficulty unchanged
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 9.3, 9.4_

- [x] 6. Persist and apply difficulty (Extra 1)
  - In `MainActivity` define `PREF_DIFFICULTY = "difficulty"` (int, same `"ttt_prefs"` file)
  - After `mGame = new TicTacToeGame()`, read `int idx = prefs.getInt(PREF_DIFFICULTY, Difficulty.indexFor(DifficultyLevel.Expert))` and apply `mGame.setDifficultyLevel(Difficulty.difficultyFor(idx))` (both `onCreate` branches, before `startNewGame()`/restore)
  - Write the encoded difficulty immediately when the difficulty dialog changes it, and also in `onStop`; must not reset to Expert on recreation/relaunch
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

- [x] 7. Create the landscape layout
  - Create `app/src/main/res/layout-land/activity_main.xml` with a slim/compact `MaterialToolbar` (`R.id.toolbar`, blank title, measured height ≤ 56dp) pinned to the top for the overflow menu only
  - Place `BoardView` (`R.id.board_view`) at 270dp × 270dp (±10dp) on the left, start-constrained to the content area below the toolbar
  - Place `information` (`R.id.information`) and the score `TextView` (shared id) on the right, start-constrained to the end of the board
  - Keep `setSupportActionBar` null-guarded in `onCreate` so `R.id.toolbar` works in both layouts; leave portrait unchanged
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 2.2, 2.3, 2.4, 2.5, 2.6_

- [x] 8. Add save/restore, `mGoFirst`, and status tracking
  - In `MainActivity` add `mGoFirst` (default `true`) and `mStatus` (current `GameStatus`); set `mStatus` wherever the info text is set
  - Implement `onSaveInstanceState(Bundle)` writing `KEY_BOARD` (from `mGame.getBoardState()`), `KEY_GAME_OVER`, `KEY_STATUS_ORDINAL` (ordinal 0..5), `KEY_HUMAN_WINS`, `KEY_COMPUTER_WINS`, `KEY_TIES`, `KEY_GO_FIRST`, `KEY_COMPUTER_TURN`, `KEY_PENDING_COMPUTER`
  - Add the `onCreate` recreation branch: validate first (null/length≠9 board OR ordinal outside 0..5 → fresh game with `mGoFirst=true`), else `setBoardState(board)`, restore the booleans (`mGoFirst` defaults true, others false) and the three scores (default 0) + `displayScores()`, re-resolve status from `GameStatus.values()[ordinal]`, set `mInfoTextView`, set `mStatus`, and call `mBoardView.invalidate()`
  - Null-savedInstanceState path starts a fresh game with `mGoFirst = true`
  - _Requirements: 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 5.1, 5.2, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4, 12.1_

- [x] 9. Add the delayed computer move and pending-computer handling (Extra 2)
  - In `MainActivity` add `DELAY_MS = 1000`, a `Handler` on the main `Looper`, and a named `mComputerMoveRunnable` field
  - **Keep the input gate at the top of `onMoveSelected`**: reject human input and `return` early (without applying a move) when either `mGameOver == true` **or** `mComputerTurn == true`; this prevents human taps during the ~1-second computer delay from being accepted
  - This task **finalizes the half-turn split** from task 2: `MainActivity` switches to `TurnOrchestrator.applyHumanMove` + the deferred `playComputerMove`, and the old combined `onMoveSelected` orchestration path is retired (consistent with task 2's note that the obsolete turn-flow logic must be removed)
  - Split `onMoveSelected`: apply the human move immediately via `TurnOrchestrator.applyHumanMove` + `mBoardView.invalidate()` + status/`mGameOver`; if the move ends the game, increment scores and **return without scheduling**; otherwise set `mComputerTurn = true`, `pendingComputer = true`, set the "computer's turn" info text, and `postDelayed(mComputerMoveRunnable, DELAY_MS)`
  - Implement `mComputerMoveRunnable`: capture `wasPending`, clear `pendingComputer` first, return if `!wasPending || mGameOver`, else call `TurnOrchestrator.playComputerMove`, clear `mComputerTurn`, and on applied result update `mStatus`/info text, `mGameOver`, scores + `displayScores()`, then `mBoardView.invalidate()` on the current Activity
  - Add `mComputerTurn`/`pendingComputer` to the save/restore in task 8; call `removeCallbacks(mComputerMoveRunnable)` in `onPause`; on `onCreate` restore, if `pendingComputer && !mGameOver`, reschedule after `DELAY_MS`
  - _Requirements: 5.1, 5.3, 5.4, 5.5, 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.7, 11.8, 11.9, 11.10, 11.11, 12.4, 13.4_

- [x] 10. Build and run minimal manual verification
  - Compile the project with `gradlew assembleDebug` and confirm the build is SUCCESSFUL
  - Run the 7 manual verification tests (design §12 / requirements §Verification): orientation + landscape menu; turn restoration; computer move around recreation (rotate during the ~1 s delay); score persistence; difficulty persistence; reset scores; and Challenge 5 regression sanity (New Game, difficulty dialog, sounds, board rendering, Quit removed)
  - _Requirements: 1.1-1.7, 2.1-2.6, 3.1-3.9, 4.1-4.7, 5.1-5.5, 6.1-6.4, 7.1-7.6, 8.1-8.4, 9.1-9.6, 10.1-10.6, 11.1-11.11, 12.1-12.5, 13.1-13.4, 14.1_

## Notes

- Sub-tasks marked with `*` are optional focused JVM unit tests over the pure additions; they can be skipped without affecting the deliverable, honoring the minimal-and-manual verification constraint. There is no "Correctness Properties" section in the design, so no property-based tests are included.
- Each top-level step is independently buildable and leaves the app working, matching the design's Section 14 order.
- Each task references the specific requirements it implements for traceability.
- `BoardView`, `GameStatus`, `Difficulty`, `Game`, `SoundPlayer`, and `SoundLifecycle` are not modified; the existing Challenge 5 architecture is preserved.
- The only intentional change to the Challenge 5 turn flow is the ~1 s computer-move delay in task 9.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1", "2", "3", "7"] },
    { "id": 1, "tasks": ["1.1", "2.1", "4"] },
    { "id": 2, "tasks": ["5"] },
    { "id": 3, "tasks": ["6"] },
    { "id": 4, "tasks": ["8"] },
    { "id": 5, "tasks": ["9"] }
  ]
}
```

Dependency rationale:
- Wave 0 — independent files: `TicTacToeGame` (1), `TurnOrchestrator` (2), strings/menu/portrait layout (3), and the new landscape layout (7) touch different files and run in parallel.
- Wave 1 — optional unit tests for the pure additions (1.1, 2.1) can run once their sources exist; `MainActivity` scores (4) depends on the strings/menu/scoreboard from task 3.
- Waves 2-5 — tasks 5, 6, 8, 9 all modify `MainActivity.java`, so they are serialized to avoid write conflicts and to respect data dependencies: SharedPreferences scores (5) builds on scores (4); difficulty persistence (6) reuses the same prefs; save/restore (8) depends on the full-board API (task 1) and the score fields; the delayed computer move (9) depends on the `TurnOrchestrator` split (task 2) and on save/restore (task 8) for the pending-computer flag.
- Task 10 (build + manual verification) is a checkpoint and is intentionally excluded from the wave graph.
