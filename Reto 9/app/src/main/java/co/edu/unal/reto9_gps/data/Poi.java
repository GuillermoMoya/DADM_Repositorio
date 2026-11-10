package co.edu.unal.reto9_gps.data;

import androidx.annotation.NonNull;

/**
 * Punto de interés obtenido de OpenStreetMap: nombre, categoría y coordenadas.
 */
public class Poi {

    private final String nombre;
    private final PoiCategory categoria;
    private final double latitud;
    private final double longitud;

    public Poi(@NonNull String nombre, @NonNull PoiCategory categoria,
               double latitud, double longitud) {
        this.nombre = nombre;
        this.categoria = categoria;
        this.latitud = latitud;
        this.longitud = longitud;
    }

    @NonNull
    public String getNombre() {
        return nombre;
    }

    @NonNull
    public PoiCategory getCategoria() {
        return categoria;
    }

    public double getLatitud() {
        return latitud;
    }

    public double getLongitud() {
        return longitud;
    }
}
