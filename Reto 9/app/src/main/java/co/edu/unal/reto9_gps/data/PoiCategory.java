package co.edu.unal.reto9_gps.data;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;

import co.edu.unal.reto9_gps.R;

/**
 * Categorías de puntos de interés que la app sabe consultar en OpenStreetMap.
 *
 * <p>Se eligió un conjunto reducido y liviano (hospitales, farmacias y
 * universidades) para no saturar el mapa. Cada categoría conoce:</p>
 * <ul>
 *     <li>el tag OSM {@code amenity=...} usado en la consulta Overpass,</li>
 *     <li>la clave de preferencia que la activa/desactiva,</li>
 *     <li>su color de marcador y su etiqueta legible.</li>
 * </ul>
 */
public enum PoiCategory {

    HOSPITAL("hospital", "pref_show_hospital",
            R.string.poi_hospital, R.color.marker_hospital),
    PHARMACY("pharmacy", "pref_show_pharmacy",
            R.string.poi_pharmacy, R.color.marker_pharmacy),
    UNIVERSITY("university", "pref_show_university",
            R.string.poi_university, R.color.marker_university);

    private final String amenityTag;
    private final String prefKey;
    @StringRes private final int labelRes;
    @ColorRes private final int colorRes;

    PoiCategory(String amenityTag, String prefKey,
                @StringRes int labelRes, @ColorRes int colorRes) {
        this.amenityTag = amenityTag;
        this.prefKey = prefKey;
        this.labelRes = labelRes;
        this.colorRes = colorRes;
    }

    /** Valor del tag {@code amenity} en OpenStreetMap (p. ej. "hospital"). */
    public String getAmenityTag() {
        return amenityTag;
    }

    /** Clave de la preferencia booleana que decide si se muestra. */
    public String getPrefKey() {
        return prefKey;
    }

    @StringRes
    public int getLabelRes() {
        return labelRes;
    }

    @ColorRes
    public int getColorRes() {
        return colorRes;
    }

    /** Devuelve la categoría cuyo tag coincide, o {@code null} si ninguna. */
    public static PoiCategory fromAmenity(String amenity) {
        if (amenity == null) {
            return null;
        }
        for (PoiCategory c : values()) {
            if (c.amenityTag.equals(amenity)) {
                return c;
            }
        }
        return null;
    }
}
