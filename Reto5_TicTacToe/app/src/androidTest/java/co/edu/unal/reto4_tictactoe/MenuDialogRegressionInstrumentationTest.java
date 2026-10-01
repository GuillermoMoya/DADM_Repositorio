package co.edu.unal.reto4_tictactoe;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Menu/dialog regression instrumentation test (task 10.3).
 *
 * <p>Feature: custom-boardview-graphics-sound. Verifies that swapping the button
 * grid for the custom {@link BoardView} left the options menu and its dialogs
 * unchanged (Requirements 8.5, 12.3). It launches {@link MainActivity}, opens the
 * overflow menu, clicks each item by its title text, and asserts the expected
 * dialog / status appears:
 * <ul>
 *   <li><b>New Game</b> — the item works and the information {@link android.widget.TextView}
 *       shows the new-game / human-first status ({@code R.string.first_human}).</li>
 *   <li><b>Difficulty</b> — tapping it shows the difficulty dialog (its title
 *       {@code R.string.difficulty_choose} and the level items are displayed).</li>
 *   <li><b>Quit</b> — tapping it shows the quit confirmation dialog
 *       ({@code R.string.quit_confirm_message}); it is dismissed via "No" so the
 *       activity is not finished.</li>
 * </ul>
 *
 * <p>The overflow menu is opened with
 * {@link androidx.test.espresso.Espresso#openActionBarOverflowOrOptionsMenu(android.content.Context)},
 * which needs no extra Espresso dependency.
 *
 * <p>This test requires a connected device/emulator and is run via
 * {@code connectedDebugAndroidTest}, separate from the JVM unit-test step.
 */
@RunWith(AndroidJUnit4.class)
public class MenuDialogRegressionInstrumentationTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    /** Resolves a string resource by id for text matching. */
    private static String string(int resId) {
        return ApplicationProvider.getApplicationContext().getString(resId);
    }

    /**
     * New Game menu item works: after selecting it, the information TextView
     * shows the human-first / new-game status.
     *
     * Validates: Requirements 8.5, 12.3
     */
    @Test
    public void newGameMenuItemShowsHumanFirstStatus() {
        openActionBarOverflowOrOptionsMenu(
                ApplicationProvider.getApplicationContext());
        onView(withText(string(R.string.new_game))).perform(click());

        // The information TextView reflects the new-game / human-first status.
        onView(withId(R.id.information))
                .check(matches(withText(string(R.string.first_human))));
    }

    /**
     * The Difficulty menu item opens the difficulty dialog: its title and the
     * three level items are displayed. The dialog is dismissed via Cancel so it
     * does not leak into later assertions.
     *
     * Validates: Requirements 8.5, 12.3
     */
    @Test
    public void difficultyMenuItemShowsDifficultyDialog() {
        openActionBarOverflowOrOptionsMenu(
                ApplicationProvider.getApplicationContext());
        onView(withText(string(R.string.difficulty))).perform(click());

        // The difficulty dialog title and level items are displayed.
        onView(withText(string(R.string.difficulty_choose)))
                .check(matches(isDisplayed()));
        onView(withText(string(R.string.difficulty_easy)))
                .check(matches(isDisplayed()));
        onView(withText(string(R.string.difficulty_harder)))
                .check(matches(isDisplayed()));
        onView(withText(string(R.string.difficulty_expert)))
                .check(matches(isDisplayed()));

        // Dismiss the dialog so the activity remains in a clean state.
        onView(withText(string(R.string.cancel))).perform(click());
    }

    /**
     * The Quit menu item opens the quit confirmation dialog. It is dismissed via
     * "No" so the activity is not finished, confirming the cancel path is intact.
     *
     * Validates: Requirements 8.5, 12.3
     */
    @Test
    public void quitMenuItemShowsQuitConfirmationDialog() {
        openActionBarOverflowOrOptionsMenu(
                ApplicationProvider.getApplicationContext());
        onView(withText(string(R.string.quit))).perform(click());

        // The quit confirmation message is displayed.
        onView(withText(string(R.string.quit_confirm_message)))
                .check(matches(isDisplayed()));

        // Dismiss with "No" so the activity is not finished.
        onView(withText(string(R.string.no))).perform(click());
    }
}
