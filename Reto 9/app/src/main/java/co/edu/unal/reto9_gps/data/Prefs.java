package co.edu.unal.reto9_gps.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Acceso centralizado a las preferencias del usuario (SharedPreferences).
 *
 * <p>Expone el radio de búsqueda en kilómetros y las categorías de POI
 * activas. El radio se almacena como entero en pasos de 0.5 km
 * (clave {@code pref_radius_steps}), por lo que km = pasos / 2.</p>
 */
public final class Prefs {

    public static final String KEY_RADIUS_STEPS = "pref_radius_steps";

    /** Valor por defecto del SeekBar: 4 pasos = 2.0 km. */
    private static final int DEFAULT_RADIUS_STEPS = 4;

    private Prefs() {
        // Clase de utilidades.
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    /** Radio de búsqueda en kilómetros (p. ej. 2.0). */
    public static double getRadiusKm(Context context) {
        int steps = prefs(context).getInt(KEY_RADIUS_STEPS, DEFAULT_RADIUS_STEPS);
        return steps / 2.0;
    }

    /** Radio de búsqueda en metros, listo para la consulta Overpass. */
    public static int getRadiusMeters(Context context) {
        return (int) Math.round(getRadiusKm(context) * 1000.0);
    }

    /** Categorías actualmente habilitadas en preferencias. */
    public static List<PoiCategory> getEnabledCategories(Context context) {
        SharedPreferences p = prefs(context);
        List<PoiCategory> activas = new ArrayList<>();
        for (PoiCategory c : PoiCategory.values()) {
            // Mismo valor por defecto que en root_preferences.xml.
            boolean defaultOn = c != PoiCategory.UNIVERSITY;
            if (p.getBoolean(c.getPrefKey(), defaultOn)) {
                activas.add(c);
            }
        }
        return activas;
    }
}
