# Requirements Document

## Introduction

Challenge 6 (`orientation-and-state-persistence`) extends the existing, working Challenge 5 Tic-Tac-Toe Android app (module `app`, package `co.edu.unal.reto4_tictactoe`) with device-orientation support, dedicated transient game-state save/restore across Activity recreation, durable score and difficulty persistence, a Reset Scores menu action (replacing Quit), and an intentionally delayed computer move so the "Activity recreated between the human move and the computer's reply" scenario is reproducible and verifiable.

These requirements are derived from the approved design document (`design.md`) and map directly to the real architecture: `MainActivity` (the only `Activity`), `TicTacToeGame` (authoritative model), `BoardView` (stateless renderer), `TurnOrchestrator` (pure turn-flow owner) plus the `Game` interface, `GameStatus`, `Difficulty`, and `SoundPlayer`/`SoundLifecycle`. The existing Challenge 5 architecture is preserved; the only intentional change to the Challenge 5 turn flow is the ~1 second computer-move delay introduced for Extra 2.

Verification is intentionally minimal and manual (the 7 manual tests mirrored from the design); no large automated test suite is required.

## Glossary

- **MainActivity**: The single Android `Activity` in package `co.edu.unal.reto4_tictactoe` that owns the UI, lifecycle, state save/restore, scores, persistence, and computer-move scheduling.
- **TicTacToeGame**: The authoritative game model holding the 9-cell board (`char[] mBoard`) and difficulty level.
- **BoardView**: The custom view that renders purely from `TicTacToeGame` state and reports taps; it holds no authoritative board state and repaints via `invalidate()`.
- **TurnOrchestrator**: The pure (Android-free) class that owns turn flow; extended with `applyHumanMove` and `playComputerMove`.
- **Game**: The interface (`getBoardState(int)`, `setMove(char,int)`, `checkForWinner()`, `getComputerMove()`) that `TurnOrchestrator` operates on; `adapt(TicTacToeGame)` wraps the model.
- **GameStatus**: The pure enum of status values; its **ordinal** is persisted so the status survives locale changes.
- **Difficulty**: The pure helper mapping difficulty level to/from an int index (0 = Easy, 1 = Harder, 2 = Expert) via `indexFor` and `difficultyFor`.
- **SoundPlayer / SoundLifecycle**: Pure seams mirroring `MediaPlayer` lifecycle; not modified in Challenge 6.
- **savedInstanceState Bundle**: The framework `Bundle` holding transient per-game state across Activity recreation (rotation).
- **ttt_prefs**: The `SharedPreferences` file (`MODE_PRIVATE`) holding durable scores and difficulty.
- **DELAY_MS**: The constant (~1000 ms) by which the computer move is deferred.
- **mComputerMoveRunnable**: The named `Runnable` field on `MainActivity` that performs the deferred computer move.
- **pendingComputer**: The boolean flag meaning "the computer still owes a move for the current turn"; persisted as `KEY_PENDING_COMPUTER`.
- **mComputerTurn**: The transient boolean gate indicating whose turn it currently is.
- **mGoFirst**: The boolean field on `MainActivity` (default `true` = human first) that records who takes the first move; preserved across recreation, with no UI or logic to change it.
- **Bundle keys**: `KEY_BOARD = "board"`, `KEY_GAME_OVER = "gameOver"`, `KEY_STATUS_ORDINAL = "statusOrdinal"`, `KEY_HUMAN_WINS = "humanWins"`, `KEY_COMPUTER_WINS = "computerWins"`, `KEY_TIES = "ties"`, `KEY_GO_FIRST = "goFirst"`, `KEY_COMPUTER_TURN = "computerTurn"`, `KEY_PENDING_COMPUTER = "pendingComputer"`.
- **SharedPreferences keys**: `PREF_HUMAN_WINS = "humanWins"`, `PREF_COMPUTER_WINS = "computerWins"`, `PREF_TIES = "ties"`, `PREF_DIFFICULTY = "difficulty"`.

## Requirements

### Requirement 1: Orientation Support and Landscape Layout

**User Story:** As a player, I want the game to work in both portrait and landscape, so that I can play comfortably however I hold my device.

#### Acceptance Criteria

1. WHILE the device is in portrait orientation, THE MainActivity SHALL render the portrait layout `res/layout/activity_main.xml` with its existing view hierarchy and view identifiers unchanged.
2. WHILE the device is in landscape orientation, THE MainActivity SHALL render the landscape layout `res/layout-land/activity_main.xml`, and the rendered landscape layout SHALL contain the same view identifiers used by the portrait layout (`R.id.toolbar`, `R.id.board_view`, `R.id.information`, and the score TextView identifier).
3. WHILE the device is in landscape orientation, THE MainActivity SHALL display the MaterialToolbar (`R.id.toolbar`) pinned to the top of the screen with no title text shown and with a measured toolbar height no greater than 56dp, in place of the large title bar.
4. WHILE the device is in landscape orientation, THE MainActivity SHALL display a menu overflow affordance in the MaterialToolbar (`R.id.toolbar`) that the player can tap to open the options menu.
5. WHILE the device is in landscape orientation, THE MainActivity SHALL position the BoardView (`R.id.board_view`) with a width of 270dp (±10dp) and a height of 270dp (±10dp), with its start edge constrained to the start of the content area below the toolbar.
6. WHILE the device is in landscape orientation, THE MainActivity SHALL position the information TextView (`R.id.information`) and the score TextView so that their start edges are constrained to the end (right) edge of the BoardView (`R.id.board_view`).
7. THE MainActivity SHALL resolve the views `R.id.toolbar`, `R.id.board_view`, `R.id.information`, and the score TextView by their shared identifiers in both the portrait and landscape layouts using a single `findViewById` path with no orientation-specific branching.

### Requirement 2: Menu Access in Both Orientations

**User Story:** As a player, I want to reach New Game, Difficulty, and Reset Scores in any orientation, so that all game actions remain available after rotating.

#### Acceptance Criteria

1. THE MainActivity SHALL present exactly the three options-menu items New Game, Difficulty, and Reset Scores in both portrait and landscape orientations, and SHALL present no Quit menu item in either orientation.
2. WHILE the device is in landscape orientation, WHEN the player taps the overflow button in the MaterialToolbar (`R.id.toolbar`), THE MainActivity SHALL display the New Game, Difficulty, and Reset Scores menu items.
3. WHILE the device is in portrait orientation, WHEN the player taps the overflow button in the MaterialToolbar (`R.id.toolbar`), THE MainActivity SHALL display the New Game, Difficulty, and Reset Scores menu items.
4. WHEN `MainActivity.onCreate` configures the support action bar and the toolbar view (`R.id.toolbar`) is present, THE MainActivity SHALL call `setSupportActionBar` with that toolbar.
5. IF `MainActivity.onCreate` configures the support action bar and the toolbar view (`R.id.toolbar`) is absent, THEN THE MainActivity SHALL skip the `setSupportActionBar` call and continue `onCreate` without raising an error.
6. THE MainActivity SHALL include a view with identifier `R.id.toolbar` in both the portrait and landscape layouts.

### Requirement 3: Transient Game-State Save and Restore

**User Story:** As a player, I want my in-progress game preserved when the device rotates, so that rotation never loses the board, status, or scores.

#### Acceptance Criteria

1. WHEN the framework calls `MainActivity.onSaveInstanceState`, THE MainActivity SHALL store the 9-cell board as a char array under `KEY_BOARD = "board"`, obtained from `TicTacToeGame.getBoardState()`.
2. WHEN the framework calls `MainActivity.onSaveInstanceState`, THE MainActivity SHALL store `mGameOver` under `KEY_GAME_OVER = "gameOver"`, `mComputerTurn` under `KEY_COMPUTER_TURN = "computerTurn"`, and the pending-computer flag under `KEY_PENDING_COMPUTER = "pendingComputer"`.
3. WHEN the framework calls `MainActivity.onSaveInstanceState`, THE MainActivity SHALL store the current GameStatus ordinal (an integer in the range 0 to 5 inclusive) under `KEY_STATUS_ORDINAL = "statusOrdinal"`, so that the status survives a locale change rather than freezing a localized string.
4. WHEN the framework calls `MainActivity.onSaveInstanceState`, THE MainActivity SHALL store `mHumanWins` under `KEY_HUMAN_WINS = "humanWins"`, `mComputerWins` under `KEY_COMPUTER_WINS = "computerWins"`, and `mTies` under `KEY_TIES = "ties"`.
5. WHEN the framework calls `MainActivity.onSaveInstanceState`, THE MainActivity SHALL store `mGoFirst` under `KEY_GO_FIRST = "goFirst"`.
6. WHEN `MainActivity.onCreate` receives a non-null savedInstanceState that contains a `KEY_BOARD` char array of exactly 9 elements, THE MainActivity SHALL restore the board into TicTacToeGame via `setBoardState`, restore the game-over, computer-turn, pending-computer, and `mGoFirst` booleans (defaulting any absent boolean key to false except `mGoFirst` which defaults to true), restore the three score integers (defaulting any absent score key to 0), and re-resolve the GameStatus from its stored ordinal.
7. IF `MainActivity.onCreate` receives a non-null savedInstanceState whose `KEY_BOARD` value is null or is a char array whose length is not exactly 9, OR whose `KEY_STATUS_ORDINAL` value is outside the range 0 to 5 inclusive, THEN THE MainActivity SHALL start a fresh game with `mGoFirst` defaulted to true rather than applying the invalid restored state.
8. WHEN `MainActivity.onCreate` successfully restores state from a non-null savedInstanceState, THE MainActivity SHALL call `BoardView.invalidate()` so that the BoardView repaints from the restored TicTacToeGame state.
9. WHEN `MainActivity.onCreate` receives a null savedInstanceState, THE MainActivity SHALL start a fresh game with `mGoFirst` defaulted to true.

### Requirement 4: Full-Board Serialization API on TicTacToeGame

**User Story:** As a developer, I want to serialize and restore the whole board, so that game state can be saved into the Bundle without BoardView holding state.

#### Acceptance Criteria

1. WHEN `getBoardState():char[]` is called, THE TicTacToeGame SHALL return a char array of exactly 9 elements containing all 9 board cells in index order 0 through 8.
2. THE TicTacToeGame SHALL return from `getBoardState():char[]` a defensive copy such that any mutation of the returned array leaves the internal board unchanged.
3. WHEN `setBoardState(char[])` is called with a non-null array of exactly 9 elements, THE TicTacToeGame SHALL replace its internal board with a defensive copy of the provided cells such that any subsequent mutation of the caller's array leaves the internal board unchanged.
4. IF `setBoardState(char[])` is called with a null array or an array whose length is not exactly 9, THEN THE TicTacToeGame SHALL leave its internal board unchanged.
5. THE TicTacToeGame SHALL retain the existing single-cell `getBoardState(int)` method as a distinct overload alongside the new full-board methods.
6. THE TicTacToeGame SHALL preserve its existing game logic unchanged when the full-board serialization methods are added.
7. THE BoardView SHALL hold no authoritative board state and SHALL render solely from TicTacToeGame.

### Requirement 5: Turn Restoration Correctness

**User Story:** As a player, I want the correct player to act after rotation, so that no move is duplicated, skipped, or stuck.

#### Acceptance Criteria

1. WHEN the Activity is recreated during an in-progress game, THE MainActivity SHALL allow exactly the player whose turn it is to act next.
2. WHEN the Activity is recreated, THE MainActivity SHALL NOT apply an extra human move.
3. WHEN the Activity is recreated, THE MainActivity SHALL apply at most one computer move for the current turn.
4. IF it is not a given player's turn, THEN THE MainActivity SHALL reject a move attempt by that player.
5. WHEN the Activity is recreated during an in-progress game, THE MainActivity SHALL keep the game able to continue so that it is never left stuck.

### Requirement 6: Who-Goes-First State (mGoFirst)

**User Story:** As a developer, I want who-goes-first represented as preserved state, so that the first-move setting is consistent across recreation without adding new UI.

#### Acceptance Criteria

1. THE MainActivity SHALL define a boolean field `mGoFirst` that defaults to `true`, representing that the human takes the first move.
2. WHEN the Activity is recreated, THE MainActivity SHALL restore `mGoFirst` from `KEY_GO_FIRST` using a default of `true`.
3. THE MainActivity SHALL provide no user interface or logic for choosing who goes first.
4. THE MainActivity SHALL use `mGoFirst` as the single representation of who goes first.

### Requirement 7: Score Management

**User Story:** As a player, I want my wins, losses, and ties tracked and shown, so that I can see my results across games.

#### Acceptance Criteria

1. THE MainActivity SHALL define integer score fields `mHumanWins`, `mComputerWins`, and `mTies`.
2. THE MainActivity SHALL provide a single `displayScores()` method as the only UI update point for the score display.
3. WHEN a game ends and the applied result's `winner()` is 2, THE MainActivity SHALL increment `mHumanWins` and then call `displayScores()`.
4. WHEN a game ends and the applied result's `winner()` is 3, THE MainActivity SHALL increment `mComputerWins` and then call `displayScores()`.
5. WHEN a game ends and the applied result's `winner()` is 1, THE MainActivity SHALL increment `mTies` and then call `displayScores()`.
6. THE MainActivity SHALL display the three scores through a score TextView present in both the portrait and landscape layouts.

### Requirement 8: Score Persistence in SharedPreferences

**User Story:** As a player, I want my scores to survive closing the app, so that my totals are not lost between sessions.

#### Acceptance Criteria

1. THE MainActivity SHALL store scores in the SharedPreferences file `"ttt_prefs"` using keys `PREF_HUMAN_WINS = "humanWins"`, `PREF_COMPUTER_WINS = "computerWins"`, and `PREF_TIES = "ties"`.
2. WHEN `MainActivity.onCreate` runs at startup, THE MainActivity SHALL load the three score integers from `"ttt_prefs"` using a default of 0 for each absent key, then call `displayScores()`.
3. WHEN `MainActivity.onStop` runs, THE MainActivity SHALL write the three current score integers to `"ttt_prefs"`.
4. THE MainActivity SHALL keep transient game state in the savedInstanceState Bundle and durable scores and difficulty in SharedPreferences as separate concerns.

### Requirement 9: Reset Scores Menu Item and Quit Removal

**User Story:** As a player, I want to reset my scores to zero, so that I can start fresh without affecting my difficulty setting.

#### Acceptance Criteria

1. WHEN the player selects the Reset Scores menu item, THE MainActivity SHALL set `mHumanWins`, `mComputerWins`, and `mTies` to 0.
2. WHEN the player selects the Reset Scores menu item, THE MainActivity SHALL call `displayScores()` to update the UI immediately.
3. WHEN the player selects the Reset Scores menu item, THE MainActivity SHALL write the three zeroed score integers to `"ttt_prefs"` immediately.
4. WHEN the player selects the Reset Scores menu item, THE MainActivity SHALL leave the persisted difficulty unchanged.
5. THE MainActivity SHALL omit the Quit menu item from the options menu and SHALL remove the `R.id.quit` handler branch from `onOptionsItemSelected`.
6. THE MainActivity SHALL keep the New Game and Difficulty menu items functional.

### Requirement 10: Difficulty Persistence (Extra 1)

**User Story:** As a player, I want my chosen difficulty remembered, so that the game does not silently reset to Expert when I rotate or relaunch.

#### Acceptance Criteria

1. THE MainActivity SHALL store the difficulty as an integer under `PREF_DIFFICULTY = "difficulty"` in the `"ttt_prefs"` file.
2. WHEN persisting the difficulty, THE MainActivity SHALL encode the difficulty level via `Difficulty.indexFor` (0 = Easy, 1 = Harder, 2 = Expert).
3. WHEN loading the difficulty, THE MainActivity SHALL decode the stored integer via `Difficulty.difficultyFor` and apply it through `setDifficultyLevel` after constructing TicTacToeGame.
4. WHEN the player changes the difficulty in the difficulty dialog, THE MainActivity SHALL write the encoded difficulty to `"ttt_prefs"` immediately.
5. WHEN `MainActivity.onStop` runs, THE MainActivity SHALL write the current encoded difficulty to `"ttt_prefs"`.
6. WHEN the Activity is recreated or the app is relaunched, THE MainActivity SHALL apply the persisted difficulty so that it does not reset to Expert.

### Requirement 11: Delayed Computer Move and Pending-Computer Handling (Extra 2)

**User Story:** As a player, I want the computer to reply after a brief pause that survives rotation, so that the computer's move completes exactly once even if I rotate while it is pending.

#### Acceptance Criteria

1. THE MainActivity SHALL define a constant `DELAY_MS` equal to 1000 milliseconds (acceptable scheduling tolerance 950 to 1050 milliseconds), a Handler bound to the main Looper, and a named Runnable field `mComputerMoveRunnable`.
2. WHEN the player selects an empty cell, THE MainActivity SHALL apply the human move immediately via `TurnOrchestrator.applyHumanMove` with no scheduling delay.
3. WHEN a human move is applied and the game is not over, THE MainActivity SHALL set `mComputerTurn` to true, set the pending-computer flag to true, and schedule `mComputerMoveRunnable` on the Handler to run after `DELAY_MS`.
4. WHEN `mComputerMoveRunnable` runs and its single-shot guard passes, THE MainActivity SHALL perform the computer move via `TurnOrchestrator.playComputerMove`.
5. IF a human move ends the game, THEN THE MainActivity SHALL NOT set the pending-computer flag and SHALL NOT schedule `mComputerMoveRunnable`.
6. WHEN `MainActivity.onPause` runs, THE MainActivity SHALL call `removeCallbacks(mComputerMoveRunnable)` on the Handler to cancel any pending computer move before the Activity is destroyed.
7. WHEN the Activity is recreated with the pending-computer flag set to true and the game not over, THE MainActivity SHALL reschedule `mComputerMoveRunnable` on its own Handler after `DELAY_MS`.
8. WHEN `mComputerMoveRunnable` runs, THE MainActivity SHALL clear the pending-computer flag to false before applying the computer move, and SHALL apply the computer move only if the pending-computer flag was true and the game was not over at the start of the run; otherwise THE MainActivity SHALL return without applying any move, so that the computer move runs at most once per schedule.
9. WHEN the computer move completes, THE MainActivity SHALL clear `mComputerTurn` to false and clear the pending-computer flag to false.
10. WHEN the computer move completes, THE MainActivity SHALL update the information text, invalidate the board, and update the displayed scores on the current (recreated) Activity instance.
11. THE TurnOrchestrator SHALL provide pure `applyHumanMove` and `playComputerMove` methods and SHALL remain the owner of the game-flow logic.

### Requirement 12: Lifecycle Additions

**User Story:** As a developer, I want minimal, well-separated lifecycle methods, so that persistence, audio, and scheduling each run at the correct boundary.

#### Acceptance Criteria

1. THE MainActivity SHALL implement `onSaveInstanceState` to save transient game state.
2. THE MainActivity SHALL implement `onStop` to persist durable scores and difficulty.
3. THE MainActivity SHALL create the MediaPlayer instances in `onResume` and release them in `onPause`.
4. WHEN `MainActivity.onPause` runs, THE MainActivity SHALL cancel any pending computer-move callback via `removeCallbacks`.
5. THE MainActivity SHALL keep lifecycle additions minimal.

### Requirement 13: Preserve Challenge 5 Functionality

**User Story:** As a player, I want all existing gameplay to keep working, so that Challenge 6 adds capability without regressions.

#### Acceptance Criteria

1. THE MainActivity SHALL preserve BoardView rendering, touch-to-cell mapping, human moves, computer moves, turn gating, and win/loss/tie detection.
2. THE MainActivity SHALL preserve the difficulty levels, the New Game action, the Difficulty menu, and the move sounds.
3. THE MainActivity SHALL preserve the existing TurnOrchestrator architecture as the single source of truth for turn flow.
4. THE MainActivity SHALL introduce no change to the Challenge 5 turn flow other than the approximately 1 second computer-move delay for Extra 2.

### Requirement 14: Build Success

**User Story:** As a developer, I want the project to keep building, so that Challenge 6 is deliverable.

#### Acceptance Criteria

1. WHEN the project is built after the Challenge 6 changes, THE build SHALL complete successfully.

## Non-Functional Requirements and Constraints

1. THE app SHALL target `minSdk 24` with compile and target SDK 37 and Java 11.
2. THE implementation SHALL preserve the existing Challenge 5 architecture, including the pure helper classes (`TurnOrchestrator`, `GameStatus`, `Difficulty`, `SoundPlayer`, `SoundLifecycle`) and the stateless `BoardView`.
3. THE implementation SHALL keep lifecycle additions minimal, limited to `onSaveInstanceState` and `onStop` plus the `removeCallbacks` addition in `onPause`.
4. THE verification approach SHALL remain minimal and manual, mirroring the 7 manual verification tests from the design, and SHALL NOT require a large automated test suite.

## Verification (Manual, mirrored from the design)

1. Orientation and landscape menu: rotate during an in-progress game; the board, marks, status text, and scores are preserved; landscape shows the board on the left with info and scores on the right and only a slim toolbar (blank title) at the top; portrait shows the normal toolbar; the overflow button reaches New Game, Difficulty, and Reset Scores in both orientations.
2. Turn restoration: make a human move, rotate, and confirm exactly the correct player acts with no extra, duplicate, or skipped move and the game is not stuck.
3. Computer move around recreation: make a non-ending human move, rotate during the ~1 s delay, and confirm the computer completes exactly one move after recreation with no crash and no duplicate; confirm a game-ending human move triggers no computer move.
4. Score persistence: win/lose/tie a few games, close and reopen the app, and confirm scores reappear; rotate and confirm scores persist.
5. Difficulty persistence: set difficulty to Easy or Harder, close and reopen the app, and confirm the dialog preselects the saved level without resetting to Expert.
6. Reset Scores: tap Reset Scores, confirm all three counters show 0 immediately, reopen the app, and confirm they remain 0.
7. Regression sanity: New Game clears the board and status; the Difficulty dialog still works; move sounds still play; board rendering is unchanged; the Quit menu item is gone.
