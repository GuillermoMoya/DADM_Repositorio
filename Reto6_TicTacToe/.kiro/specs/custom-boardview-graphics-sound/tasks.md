# Implementation Plan: Custom BoardView with Graphics and Sound

## Overview

This plan replaces the button-based 3x3 board with a custom `BoardView` (extends `android.view.View`) that draws the grid and marks on a `Canvas`, maps touches to board positions, and plays move sounds via `MediaPlayer`. Work proceeds incrementally: extract pure, JVM-testable helpers first and cover them with property/unit tests, then build the Android `BoardView`, add resources, swap the layout, rewire `MainActivity`, add instrumentation coverage, and verify the build. `TicTacToeGame` is kept unchanged. Each task builds on the previous ones and ends by wiring pieces together so no code is left orphaned.

Implementation language: **Java** (matches the existing project and the design).

## Tasks

- [x] 1. Extract pure, Android-free helper classes for testability
  - [x] 1.1 Create `BoardGeometry` pure helper class
    - Create `app/src/main/java/co/edu/unal/reto4_tictactoe/BoardGeometry.java` in package `co.edu.unal.reto4_tictactoe` with no Android imports
    - Implement `static int mapTouch(int x, int y, int cellW, int cellH, int gridStroke)` returning a row-major position `0..8` for interior touches and `-1` (NONE) for not-yet-laid-out (`cellW<=0`/`cellH<=0`), out-of-bounds, and grid-line-band touches, per the Touch-To-Position Mapping Algorithm; clamp `col`/`row` to `0..2`
    - Implement `static Mark markFor(char state)` returning `HUMAN`/`COMPUTER`/`NONE` for `HUMAN_PLAYER`/`COMPUTER_PLAYER`/`OPEN_SPOT` (define a `Mark` enum, e.g. nested `BoardGeometry.Mark`)
    - Implement `static int[] scaledSize(int bitmapW, int bitmapH, int cellW, int cellH, int inset)` returning `{width, height}` that fits within the cell (minus inset) preserving aspect ratio
    - _Requirements: 3.2, 4.2, 4.3, 4.4, 4.5, 6.1, 6.2, 6.3, 12.1, 12.2_

  - [x] 1.2 Create `MoveGate` pure helper class
    - Create `app/src/main/java/co/edu/unal/reto4_tictactoe/MoveGate.java` in package `co.edu.unal.reto4_tictactoe` with no Android imports
    - Implement `static boolean shouldApply(char state, boolean gameOver, boolean computerTurn)` returning `true` iff `state == OPEN_SPOT` and `!gameOver` and `!computerTurn`
    - _Requirements: 6.4, 6.5, 6.6, 9.4, 12.1, 12.2_

- [x] 2. Add the property-testing dependency and cover pure helpers with tests
  - [x] 2.1 Add jqwik as a test dependency
    - Add `testImplementation` for jqwik (JUnit 5 property testing) in `app/build.gradle.kts`; enable the JUnit Platform for unit tests as required by jqwik (`testOptions { unitTests.all { it.useJUnitPlatform() } }` or equivalent). Do NOT implement property-based testing from scratch
    - _Requirements: 12.1, 12.2_

  - [x] 2.2 Write property test for touch mapping (`BoardGeometry.mapTouch`)
    - Create `app/src/test/java/co/edu/unal/reto4_tictactoe/BoardGeometryMapTouchPropertyTest.java`
    - Generate `(x, y)` across in-bounds interiors, edges, grid-line bands, and out-of-bounds with positive cell dims; assert correct row-major `0..8` for interior touches and `-1` otherwise; minimum 100 iterations (`@Property(tries = 100)`)
    - **Property 1: Touch mapping is a total function to a valid cell or none**
    - Tag: `// Feature: custom-boardview-graphics-sound, Property 1: Touch mapping is a total function to a valid cell or none`
    - _Requirements: 6.1, 6.2, 6.3_

  - [x] 2.3 Write property test for the draw decision (`BoardGeometry.markFor`)
    - Create `app/src/test/java/co/edu/unal/reto4_tictactoe/BoardGeometryMarkForPropertyTest.java`
    - Generate `char[9]` boards of `HUMAN_PLAYER`/`COMPUTER_PLAYER`/`OPEN_SPOT`; assert per-cell decision matches state and an all-open board yields zero marks; minimum 100 iterations
    - **Property 4: Board rendering reflects getBoardState exactly**
    - Tag: `// Feature: custom-boardview-graphics-sound, Property 4: Board rendering reflects getBoardState exactly`
    - _Requirements: 4.2, 4.3, 4.4, 5.1, 7.1, 7.2, 7.3, 11.1_

  - [x] 2.4 Write property test for cell-dimension tiling
    - Create `app/src/test/java/co/edu/unal/reto4_tictactoe/BoardGeometryCellDimsPropertyTest.java`
    - Generate `w, h > 0`; assert cell width/height equal `w/3`/`h/3` and the nine cells tile the board region (up to integer rounding); minimum 100 iterations
    - **Property 5: Cell dimensions tile the board for any size**
    - Tag: `// Feature: custom-boardview-graphics-sound, Property 5: Cell dimensions tile the board for any size`
    - _Requirements: 3.2, 3.3_

  - [x] 2.5 Write property test for mark scaling (`BoardGeometry.scaledSize`)
    - Create `app/src/test/java/co/edu/unal/reto4_tictactoe/BoardGeometryScaledSizePropertyTest.java`
    - Generate positive cell/bitmap sizes; assert scaled width <= cell width and scaled height <= cell height; minimum 100 iterations
    - **Property 6: Marks are scaled to fit within their cell**
    - Tag: `// Feature: custom-boardview-graphics-sound, Property 6: Marks are scaled to fit within their cell`
    - _Requirements: 4.5_

  - [x] 2.6 Write property test for move gating (`MoveGate.shouldApply`)
    - Create `app/src/test/java/co/edu/unal/reto4_tictactoe/MoveGatePropertyTest.java`
    - Generate board states + `gameOver`/`computerTurn` flags; assert `shouldApply` is `true` iff cell open and both flags false, and remains correct after simulated redraws and while a delayed computer move is pending; minimum 100 iterations
    - **Property 2: A move is applied — and its sound played — exactly when the target cell is open and input is allowed**
    - Tag: `// Feature: custom-boardview-graphics-sound, Property 2: A move is applied — and its sound played — exactly when the target cell is open and input is allowed`
    - _Requirements: 6.4, 6.5, 6.6, 8.4, 9.4, 11.2, 13.3_

- [~] 3. Checkpoint - Ensure pure-helper tests pass
  - Ensure all JVM unit/property tests for `BoardGeometry` and `MoveGate` pass, ask the user if questions arise.

- [x] 4. Implement the custom BoardView
  - [x] 4.1 Create `BoardView` class with fields, constructors, and public API
    - Create `app/src/main/java/co/edu/unal/reto4_tictactoe/BoardView.java` extending `android.view.View` in package `co.edu.unal.reto4_tictactoe`
    - Add fields: `mGridPaint` (Paint), `mHumanBitmap`/`mComputerBitmap` (Bitmap), `mCellWidth`/`mCellHeight` (int), `mGame` (TicTacToeGame, non-owning), `mListener` (OnMoveListener), `GRID_STROKE` constant
    - Add constructors `BoardView(Context)` and `BoardView(Context, AttributeSet)` both delegating to a shared `initialize(Context)` that creates `mGridPaint` and decodes the mark bitmaps via `BitmapFactory.decodeResource(...)`
    - Add public API: `setGame(TicTacToeGame)`, `setOnMoveListener(OnMoveListener)`, and nested `interface OnMoveListener { void onMoveSelected(int position); }`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 5.1, 12.1, 12.2_

  - [x] 4.2 Implement `onSizeChanged` using `BoardGeometry`
    - Override `onSizeChanged(w, h, oldw, oldh)`: set `mCellWidth = w/3`, `mCellHeight = h/3`, and produce scaled mark bitmaps sized via `BoardGeometry.scaledSize(...)` and `Bitmap.createScaledBitmap`, recomputing on every size change
    - _Requirements: 3.2, 3.3, 4.5, 11.1_

  - [x] 4.3 Implement `onDraw` to render grid and marks from game state
    - Override `onDraw(Canvas)`: guard against zero cell size; draw two vertical + two horizontal grid lines forming a 3x3 arrangement; for each position `0..8` read `mGame.getBoardState(i)`, use `BoardGeometry.markFor(...)` to decide, and draw the human/computer scaled bitmap centered in the cell (null-guarded) or nothing for open cells
    - _Requirements: 3.1, 4.2, 4.3, 4.4, 4.5, 5.1, 7.1, 7.2, 7.3, 11.1_

  - [x] 4.4 Implement `onTouchEvent` delegating to `BoardGeometry.mapTouch`
    - Override `onTouchEvent(MotionEvent)`: on `ACTION_UP`, call `BoardGeometry.mapTouch(x, y, mCellWidth, mCellHeight, GRID_STROKE)`; if result >= 0 notify `mListener.onMoveSelected(position)` and return `true`; otherwise ignore (return `false`/`super`). Do NOT consult game-over/computer-turn/occupancy here
    - _Requirements: 6.1, 6.2, 6.3_

- [x] 5. Add graphics and audio resources
  - [x] 5.1 Add human and computer mark drawables
    - Add `res/drawable/human_mark` (X image) and `res/drawable/computer_mark` (O image) as PNG or vector XML (placeholder assets acceptable) so `BitmapFactory.decodeResource` resolves them
    - _Requirements: 4.1_

  - [x] 5.2 Add human and computer move sound resources
    - Create `res/raw/` and add `human_move` and `computer_move` in a `MediaPlayer`-supported format (placeholder assets acceptable) so `R.raw.human_move`/`R.raw.computer_move` resolve
    - _Requirements: 9.1_

- [x] 6. Replace the button grid with BoardView in the layout
  - [x] 6.1 Update `activity_main.xml`
    - Remove the `TableLayout` `grid_layout` and buttons `one`..`nine`; add `<co.edu.unal.reto4_tictactoe.BoardView android:id="@+id/board_view" .../>` with `layout_width="0dp"`, `layout_height="0dp"`, `app:layout_constraintDimensionRatio="1:1"`, constrained top-to-bottom-of `@id/information`, bottom-to-parent, start/end to parent; keep the MaterialToolbar and information TextView and their ConstraintLayout constraints
    - _Requirements: 2.1, 2.2, 2.3_

- [x] 7. Rewire MainActivity to the drawn board and sound
  - [x] 7.1 Remove button-based board code and add new fields
    - In `MainActivity.java` remove the `mBoardButtons` array, the per-button wiring in `startNewGame`, the `ButtonClickListener` inner class, and the button-mutating body of the old `setMove(char,int)` helper
    - Add fields `mBoardView` (BoardView), `mComputerTurn` (boolean), `mHumanMoveSound`/`mComputerMoveSound` (MediaPlayer); keep the existing `mGameOver`
    - _Requirements: 5.4, 12.1, 12.2, 12.3_

  - [x] 7.2 Wire BoardView in `onCreate`
    - In `onCreate`: keep toolbar/insets wiring; `mBoardView = findViewById(R.id.board_view)`, `mBoardView.setGame(mGame)`, `mBoardView.setOnMoveListener(this::onMoveSelected)`, then call `startNewGame()`
    - _Requirements: 1.4, 5.1, 5.2_

  - [x] 7.3 Implement `onMoveSelected(int position)` turn logic with gating and sound
    - Return early if `mGameOver` or `mComputerTurn`; return early (no sound) if `mGame.getBoardState(position) != OPEN_SPOT` (use `MoveGate.shouldApply`)
    - Apply human move via `mGame.setMove(HUMAN_PLAYER, position)`, play human sound, `mBoardView.invalidate()`; evaluate `checkForWinner()`; if `0`, enter computer turn, set status, apply `mGame.setMove(COMPUTER_PLAYER, mGame.getComputerMove())`, play computer sound, `invalidate()`, re-check winner, exit computer turn
    - Update the information TextView for continue/tie/human-win/computer-win reusing existing `R.string` resources (`turn_human`, `turn_computer`, `result_tie`, `result_human_wins`, `result_computer_wins`); set `mGameOver = true` on non-zero winner
    - _Requirements: 5.2, 5.3, 6.4, 6.5, 6.6, 7.1, 7.2, 8.2, 8.3, 8.4, 9.2, 9.3, 9.4, 11.2_

  - [x] 7.4 Update `startNewGame` and preserve menu/dialogs
    - `startNewGame()`: `mGame.clearBoard()`, `mGameOver = false`, `mComputerTurn = false`, cancel any pending delayed computer move, set human-first status, `mBoardView.invalidate()`
    - Leave the options menu, difficulty dialog, and quit dialog and their handlers unchanged
    - _Requirements: 7.3, 8.1, 8.5, 8.6, 12.3_

  - [x] 7.5 Implement MediaPlayer lifecycle and playback helper
    - `onResume()`: create `mHumanMoveSound = MediaPlayer.create(this, R.raw.human_move)` and `mComputerMoveSound = MediaPlayer.create(this, R.raw.computer_move)`
    - `onPause()`: if non-null `release()` then set each field to `null`
    - Add null-guarded `playSound(MediaPlayer mp)` (`if (mp != null) { if (mp.isPlaying()) mp.seekTo(0); mp.start(); }`) and use it for both move sounds
    - _Requirements: 9.2, 9.3, 10.1, 10.2, 10.3, 10.4_

- [~] 8. Checkpoint - Ensure app compiles and unit tests pass
  - Ensure the project compiles and all JVM unit/property tests pass, ask the user if questions arise.

- [x] 9. Add property and example tests for orchestration and mapping
  - [x] 9.1 Write property test for exactly-one computer move (`onMoveSelected` via fakes)
    - Create `app/src/test/java/co/edu/unal/reto4_tictactoe/ComputerMovePropertyTest.java`
    - Drive the relocated turn logic against a fake `TicTacToeGame` (scripted `checkForWinner`/`getComputerMove`) and a spy; assert exactly one `setMove(COMPUTER_PLAYER, m)` when the human move leaves the game continuing, and none when the human move ends the game; minimum 100 iterations
    - **Property 3: A computer move follows a valid human move exactly once while the game continues**
    - Tag: `// Feature: custom-boardview-graphics-sound, Property 3: A computer move follows a valid human move exactly once while the game continues`
    - _Requirements: 5.3, 8.2_

  - [x] 9.2 Write property test for MediaPlayer lifecycle (`onResume`/`onPause`)
    - Create `app/src/test/java/co/edu/unal/reto4_tictactoe/MediaPlayerLifecyclePropertyTest.java`
    - Extract the lifecycle logic behind a testable seam (e.g., a `MediaPlayer` factory) and run random `resume`/`pause` sequences against a lifecycle harness with a fake player; assert no released instance is ever retained (field is null after release); minimum 100 iterations
    - **Property 7: No released MediaPlayer is ever retained**
    - Tag: `// Feature: custom-boardview-graphics-sound, Property 7: No released MediaPlayer is ever retained`
    - _Requirements: 10.4_

  - [x] 9.3 Write JVM unit/example tests for status mapping, new game, difficulty, and sound
    - Create example tests for: winner-code → status-string mapping for codes 0/1/2/3; `startNewGame` sets human-first status and clears state; difficulty selection sets the correct `DifficultyLevel` enum for each choice; sound played once on a valid human move and once on a computer move via a mock/fake `MediaPlayer`
    - _Requirements: 8.1, 8.3, 8.6, 9.2, 9.3_

- [x] 10. Add instrumentation tests (device/emulator)
  - [x] 10.1 Write layout inflation and structure instrumentation test
    - Create an androidTest that inflates `activity_main`; assert `board_view` is present and is a `BoardView` in the correct package, `grid_layout`/buttons `one`..`nine` are absent, and toolbar + information TextView are present
    - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 12.2_

  - [x] 10.2 Write wiring, redraw, and grid-drawing instrumentation test
    - Assert `setGame` wiring causes marks to appear after a move (invalidate/redraw) and that the grid is drawn as a 3x3 arrangement (draw-call spy or snapshot)
    - _Requirements: 1.4, 3.1, 7.1, 7.2_

  - [x] 10.3 Write menu/dialog regression instrumentation test
    - Assert the options menu items (new game, AI difficulty, quit) open their dialogs and game logic is unchanged
    - _Requirements: 8.5, 12.3_

- [x] 11. Build and verification
  - [x] 11.1 Compile the project and run JVM unit/property tests
    - Run the Gradle assemble/compile task and the JVM unit test task (e.g., `testDebugUnitTest`); fix any compile or test failures. Note: instrumentation tests (task 10) require a connected device/emulator and are run separately (`connectedDebugAndroidTest`)
    - _Requirements: 12.1, 12.2_

- [~] 12. Final checkpoint - Ensure all tests pass
  - Ensure the project builds and all JVM unit/property tests pass, ask the user if questions arise.

- [ ] 13. OPTIONAL - Non-blocking ~1s computer move delay
  > This entire task is OPTIONAL (Requirement 13).
  - [~] 13.1 (OPTIONAL) Add delayed computer move via `Handler.postDelayed`
    - In `MainActivity`, wrap the computer-move step of `onMoveSelected` in a `Handler.postDelayed` (~1000ms) that keeps `mComputerTurn = true` while pending; retain a reference to the pending callback and cancel/guard it in `startNewGame` and on lifecycle changes so a stale move is never applied; ensure the UI thread is not blocked
    - _Requirements: 13.1, 13.2, 13.3_

  - [~] 13.2 (OPTIONAL) Add instrumentation test for the delayed move
    - Assert the UI stays responsive during the delay and the computer move lands after ≈1s; gating-while-pending is already covered by Property 2
    - _Requirements: 13.1, 13.2, 13.3_

## Notes

- Tasks marked with `*` are optional test sub-tasks and can be skipped for a faster MVP; core implementation sub-tasks are never marked optional.
- Task 13 (and its sub-tasks) is OPTIONAL per Requirement 13; the base design applies the computer move synchronously.
- Each task references specific requirement clauses for traceability; property test tasks additionally reference their design property by number and title.
- Property-based tests use jqwik (added in task 2.1) and run a minimum of 100 iterations each; PBT is not implemented from scratch.
- `TicTacToeGame` is not modified. If a change becomes strictly necessary it must be documented and limited to the interface need (Requirement 8.7).
- Instrumentation tests require a device/emulator and are outside the JVM verification step; manual audio listening is explicitly not a build task.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "2.1", "5.1", "5.2"] },
    { "id": 1, "tasks": ["2.2", "2.3", "2.4", "2.5", "2.6", "4.1"] },
    { "id": 2, "tasks": ["4.2", "4.3", "4.4"] },
    { "id": 3, "tasks": ["6.1"] },
    { "id": 4, "tasks": ["7.1"] },
    { "id": 5, "tasks": ["7.2", "7.3", "7.4", "7.5"] },
    { "id": 6, "tasks": ["9.1", "9.2", "9.3", "10.1", "10.2", "10.3", "11.1"] },
    { "id": 7, "tasks": ["13.1"] },
    { "id": 8, "tasks": ["13.2"] }
  ]
}
```
