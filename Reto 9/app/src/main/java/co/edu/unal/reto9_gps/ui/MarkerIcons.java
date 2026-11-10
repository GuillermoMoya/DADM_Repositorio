package co.edu.unal.reto9_gps.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import co.edu.unal.reto9_gps.R;
import co.edu.unal.reto9_gps.data.PoiCategory;

/**
 * Genera los iconos de marcador tiñendo el pin base ({@code ic_marker}) con el
 * color de cada categoría. Así un solo vector sirve para todas las categorías.
 */
public final class MarkerIcons {

    private MarkerIcons() {
    }

    /** Devuelve un Drawable del pin ya coloreado para la categoría dada. */
    @NonNull
    public static Drawable forCategory(@NonNull Context context, @NonNull PoiCategory category) {
        Drawable base = ContextCompat.getDrawable(context, R.drawable.ic_marker);
        if (base == null) {
            throw new IllegalStateException("Falta el drawable ic_marker");
        }
        Drawable wrapped = DrawableCompat.wrap(base.mutate());
        int color = ContextCompat.getColor(context, category.getColorRes());
        DrawableCompat.setTint(wrapped, color);
        return wrapped;
    }
}
