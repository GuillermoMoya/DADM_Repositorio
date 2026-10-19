package co.edu.unal.reto4_tictactoe;

import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.appbar.MaterialToolbar;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumentation test that launches {@link MainActivity} (which inflates
 * {@code activity_main}) and verifies the inflated view hierarchy matches the
 * BoardView-based layout.
 *
 * <p>Feature: custom-boardview-graphics-sound. Validates that:
 * <ul>
 *   <li>{@code R.id.board_view} is present and is a {@link BoardView} in the
 *       correct package (Requirements 1.1, 1.2, 1.3, 2.1).</li>
 *   <li>The old {@code grid_layout} and buttons {@code one}..{@code nine} are
 *       absent from the layout (Requirements 2.2, 2.3, 12.2). Those {@code R.id}
 *       constants no longer exist, so they are resolved by resource name at
 *       runtime and asserted to resolve to no view.</li>
 *   <li>The {@link MaterialToolbar} ({@code R.id.toolbar}) and information
 *       {@link TextView} ({@code R.id.information}) are still present
 *       (Requirements 2.1, 2.2, 2.3).</li>
 * </ul>
 *
 * <p>This test requires a connected device/emulator and is run via
 * {@code connectedDebugAndroidTest}, separate from the JVM unit-test step.
 */
@RunWith(AndroidJUnit4.class)
public class BoardViewLayoutInstrumentationTest {

    /** Resource-name lookups for ids that were removed from the layout. */
    private static final String[] REMOVED_IDS = {
            "grid_layout", "one", "two", "three", "four", "five",
            "six", "seven", "eight", "nine"
    };

    @Test
    public void boardViewReplacesButtonGrid() {
        try (ActivityScenario<MainActivity> scenario =
                     ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                // board_view is present and is a BoardView in the correct package.
                View boardView = activity.findViewById(R.id.board_view);
                assertNotNull("board_view should be present in the layout", boardView);
                assertTrue("board_view should be an instance of BoardView",
                        boardView instanceof BoardView);
                assertEquals(
                        "board_view should be co.edu.unal.reto4_tictactoe.BoardView",
                        "co.edu.unal.reto4_tictactoe.BoardView",
                        boardView.getClass().getName());

                // The old grid_layout and buttons one..nine must be absent.
                // Their R.id constants no longer exist, so resolve by name.
                String packageName = activity.getPackageName();
                for (String name : REMOVED_IDS) {
                    int id = activity.getResources()
                            .getIdentifier(name, "id", packageName);
                    if (id != 0) {
                        // The id resource may still exist elsewhere, but it must
                        // not resolve to any view in the inflated layout.
                        assertNull("Removed view '" + name
                                + "' should not be present in the layout",
                                activity.findViewById(id));
                    }
                }

                // Toolbar and information TextView remain present.
                View toolbar = activity.findViewById(R.id.toolbar);
                assertNotNull("toolbar should be present", toolbar);
                assertTrue("toolbar should be a MaterialToolbar",
                        toolbar instanceof MaterialToolbar);

                View information = activity.findViewById(R.id.information);
                assertNotNull("information should be present", information);
                assertTrue("information should be a TextView",
                        information instanceof TextView);
            });
        }
    }
}
