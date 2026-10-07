# Requirements Document

## Introduction

This feature replaces the button-based Tic-Tac-Toe board in the existing Android application (package `co.edu.unal.reto4_tictactoe`) with a custom drawn `View`, and adds graphical and audio feedback. Today the board is a 3x3 grid of Material Buttons in `activity_main.xml`, and `MainActivity` handles moves through a per-button click listener. This feature introduces a custom `BoardView` (extending `android.view.View`) that draws the grid directly on a `Canvas`, renders player marks (X and O) using bitmap/image resources, detects touch input to determine a board position (0-8), and plays sound effects for each move using `MediaPlayer`.

The existing game engine `TicTacToeGame` (pure game logic) and the existing menu, dialogs, difficulty handling, winner detection, and "human first" rule MUST be preserved. Only changes strictly necessary to support the new drawn interface are permitted, and any such change MUST be called out explicitly. The implementation MUST remain in Java and keep the existing project structure and package.

An optional enhancement delays the computer's move by approximately one second without blocking the UI thread.

## Glossary

- **App**: The existing Android Tic-Tac-Toe application in package `co.edu.unal.reto4_tictactoe`.
- **BoardView**: The new custom view class extending `android.view.View` that draws the board, renders marks, and detects touches.
- **MainActivity**: The existing activity that hosts the board, status text, options menu, and dialogs.
- **TicTacToeGame**: The existing pure game-logic class. Constants: `BOARD_SIZE`=9, `HUMAN_PLAYER`='X', `COMPUTER_PLAYER`='O', `OPEN_SPOT`=' '. Methods: `clearBoard()`, `setMove(char, int)` returns boolean, `getBoardState(int)` returns char, `getDifficultyLevel()`/`setDifficultyLevel()`, `checkForWinner()` returns int (0=continue, 1=tie, 2=human 'X' wins, 3=computer 'O' wins), `getComputerMove()` returns index 0-8 or -1. Enum `DifficultyLevel {Easy, Harder, Expert}`.
- **Board Position**: An integer index 0-8 identifying one of the nine cells, mapped row-major from the drawn grid.
- **Cell**: One of the nine square regions of the drawn board that can hold a mark or be open.
- **Grid Line Region**: The pixel area occupied by the lines separating cells, which is not part of any cell's interior.
- **Human Mark**: The image resource representing `HUMAN_PLAYER` ('X').
- **Computer Mark**: The image resource representing `COMPUTER_PLAYER` ('O').
- **Move Sound**: An audio resource in `res/raw` played when a mark is placed.
- **Human Move Sound**: The audio resource played when the human places a mark.
- **Computer Move Sound**: The audio resource played when the computer places a mark.
- **MediaPlayer**: The Android `android.media.MediaPlayer` instances used to play move sounds.
- **Game Over State**: The condition in which `checkForWinner()` has returned a non-zero value (tie or a win) and further human input is not accepted until a new game starts.
- **Computer Turn**: The interval after a valid human move during which the computer's move is being computed and rendered, and human input is not accepted.

## Requirements

### Requirement 1: Custom BoardView Class

**User Story:** As a developer, I want a custom view class that renders the game board, so that the board is drawn on a canvas instead of composed from buttons.

#### Acceptance Criteria

1. THE BoardView SHALL extend `android.view.View`.
2. THE BoardView SHALL reside in package `co.edu.unal.reto4_tictactoe`.
3. THE BoardView SHALL provide the constructors required to be instantiated from an XML layout.
4. THE BoardView SHALL expose a method that allows MainActivity to associate a TicTacToeGame instance with the BoardView.

### Requirement 2: Replace Button Board With BoardView In Layout

**User Story:** As a player, I want the board shown as a single drawn view, so that the interface uses custom graphics instead of the 3x3 button grid.

#### Acceptance Criteria

1. THE App SHALL display the BoardView in place of the TableLayout button grid (`grid_layout` and buttons `one`..`nine`) in the main screen layout.
2. THE App SHALL retain the existing MaterialToolbar and the information TextView on the main screen.
3. WHEN the main screen is displayed, THE BoardView SHALL be visible and occupy the region previously occupied by the button grid.

### Requirement 3: Draw The Board On Canvas

**User Story:** As a player, I want to see a clear grid, so that I can identify the nine cells of the board.

#### Acceptance Criteria

1. WHEN the BoardView is drawn, THE BoardView SHALL render grid lines or rectangles that divide the view into a 3x3 arrangement of nine cells directly on the Canvas.
2. THE BoardView SHALL compute cell dimensions from the BoardView's current width and height so that the nine cells fill the drawn board area.
3. WHEN the BoardView size changes, THE BoardView SHALL recompute cell dimensions used for drawing and touch mapping.

### Requirement 4: Render X And O Using Image Resources

**User Story:** As a player, I want to see distinct marks for each player, so that I can read the current state of the game.

#### Acceptance Criteria

1. THE BoardView SHALL load a Human Mark image resource and a Computer Mark image resource.
2. WHEN a Cell's board state equals `HUMAN_PLAYER`, THE BoardView SHALL draw the Human Mark image within that Cell.
3. WHEN a Cell's board state equals `COMPUTER_PLAYER`, THE BoardView SHALL draw the Computer Mark image within that Cell.
4. WHEN a Cell's board state equals `OPEN_SPOT`, THE BoardView SHALL draw no mark image within that Cell.
5. THE BoardView SHALL draw each mark image scaled to fit within the bounds of its Cell.

### Requirement 5: Connect BoardView To TicTacToeGame Logic

**User Story:** As a developer, I want the drawn board backed by the existing game engine, so that game rules are enforced by TicTacToeGame without duplication.

#### Acceptance Criteria

1. THE BoardView SHALL read each Cell's displayed state from `TicTacToeGame.getBoardState(int)` using Board Positions 0-8.
2. THE BoardView SHALL apply human moves through `TicTacToeGame.setMove(char, int)` using `HUMAN_PLAYER`.
3. THE App SHALL apply computer moves through `TicTacToeGame.setMove(char, int)` using `COMPUTER_PLAYER` and the value returned by `TicTacToeGame.getComputerMove()`.
4. THE App SHALL NOT reimplement move validation, winner detection, difficulty selection, or computer move selection outside of TicTacToeGame.

### Requirement 6: Detect Touches And Map To Board Position

**User Story:** As a player, I want to tap a cell to place my mark, so that I can play by touching the board.

#### Acceptance Criteria

1. WHEN the human touches the BoardView within a Cell interior, THE BoardView SHALL translate the touch coordinates into the corresponding Board Position (0-8) in row-major order.
2. IF a touch occurs outside the drawn board bounds, THEN THE BoardView SHALL treat the touch as not selecting any Cell and SHALL NOT change game state.
3. IF a touch occurs on a Grid Line Region between cells, THEN THE BoardView SHALL treat the touch as not selecting any Cell and SHALL NOT change game state.
4. IF a touch maps to a Cell whose board state is not `OPEN_SPOT`, THEN THE BoardView SHALL reject the move and SHALL NOT change game state.
5. WHILE the App is in Game Over State, THE BoardView SHALL ignore touches intended to place a mark and SHALL NOT change game state.
6. WHILE the App is in Computer Turn, THE BoardView SHALL ignore touches intended to place a mark and SHALL NOT change game state.

### Requirement 7: Update Visual Board After A Valid Move

**User Story:** As a player, I want the board to redraw after each move, so that I can immediately see the result of a move.

#### Acceptance Criteria

1. WHEN a valid human move is applied through `TicTacToeGame.setMove(char, int)`, THE BoardView SHALL redraw so that the Human Mark appears in the selected Cell.
2. WHEN a computer move is applied through `TicTacToeGame.setMove(char, int)`, THE BoardView SHALL redraw so that the Computer Mark appears in the computer-selected Cell.
3. WHEN the board is cleared through `TicTacToeGame.clearBoard()`, THE BoardView SHALL redraw so that all nine cells display no mark.

### Requirement 8: Preserve Existing Game Rules And Behavior

**User Story:** As a player, I want the game to behave as before, so that only the visual and audio interface changes.

#### Acceptance Criteria

1. WHEN a new game starts, THE App SHALL allow the human to move first.
2. WHEN a valid human move is applied and the game is not over, THE App SHALL request a computer move and apply it.
3. WHEN a move is applied, THE App SHALL evaluate the outcome using `TicTacToeGame.checkForWinner()` and SHALL update the information TextView to reflect continue, tie, human win, or computer win.
4. WHEN `TicTacToeGame.checkForWinner()` returns a non-zero value, THE App SHALL enter Game Over State.
5. THE App SHALL preserve the existing options menu actions (new game, AI difficulty, quit) and their dialogs.
6. THE App SHALL preserve difficulty selection through `TicTacToeGame.getDifficultyLevel()` and `TicTacToeGame.setDifficultyLevel()` with the `DifficultyLevel` enum values `Easy`, `Harder`, and `Expert`.
7. WHERE a change to TicTacToeGame is strictly necessary to support the drawn interface, THE App SHALL document that change explicitly and SHALL limit it to what the interface requires.

### Requirement 9: Sound Effects For Moves

**User Story:** As a player, I want to hear feedback when marks are placed, so that moves feel responsive.

#### Acceptance Criteria

1. THE App SHALL include a Human Move Sound audio resource and a Computer Move Sound audio resource in `res/raw`.
2. WHEN the human places a valid mark, THE App SHALL play the Human Move Sound through a MediaPlayer.
3. WHEN the computer places a mark, THE App SHALL play the Computer Move Sound through a MediaPlayer.
4. IF a touch does not result in a valid move, THEN THE App SHALL NOT play a Move Sound.

### Requirement 10: MediaPlayer Lifecycle Management

**User Story:** As a user, I want audio resources handled correctly, so that the app does not leak resources or fail after backgrounding.

#### Acceptance Criteria

1. WHEN `onResume` is invoked, THE App SHALL create the MediaPlayer instances used for the Human Move Sound and Computer Move Sound.
2. WHEN `onPause` is invoked, THE App SHALL release the MediaPlayer instances and free their resources.
3. WHEN the App is resumed after being paused, THE App SHALL recreate the MediaPlayer instances so that Move Sounds play again.
4. THE App SHALL NOT retain a MediaPlayer instance after it has been released.

### Requirement 11: Preserve State And Rendering Across Recreation

**User Story:** As a player, I want the board to stay consistent when the activity is recreated, so that a configuration change does not corrupt the display.

#### Acceptance Criteria

1. WHEN the BoardView is redrawn after a size change or recreation, THE BoardView SHALL render each Cell according to the current `TicTacToeGame.getBoardState(int)` values.
2. WHILE the App is in Game Over State, THE App SHALL continue to reject mark-placing touches after the BoardView is redrawn.

### Requirement 12: Implementation And Project Structure Constraints

**User Story:** As a developer, I want the feature to fit the existing codebase, so that structure and language remain consistent.

#### Acceptance Criteria

1. THE App SHALL implement the BoardView and all supporting changes in Java.
2. THE App SHALL keep all new source files within package `co.edu.unal.reto4_tictactoe` and the existing project structure.
3. THE App SHALL NOT modify functionality unrelated to this feature, including the options menu, dialogs, and TicTacToeGame logic, except where Requirement 8, criterion 7 applies.

### Requirement 13: Non-Blocking Computer Move Delay

**User Story:** As a player, I want a brief pause before the computer moves, so that the computer's turn feels natural.

> This requirement is OPTIONAL.

#### Acceptance Criteria

1. WHERE the optional delay is enabled, WHEN a valid human move is applied and the game is not over, THE App SHALL delay the computer's move by approximately one second.
2. WHERE the optional delay is enabled, THE App SHALL schedule the delayed computer move without blocking the UI thread (for example, using `Handler.postDelayed`).
3. WHERE the optional delay is enabled, WHILE the delayed computer move is pending, THE App SHALL remain in Computer Turn and SHALL ignore mark-placing touches.
