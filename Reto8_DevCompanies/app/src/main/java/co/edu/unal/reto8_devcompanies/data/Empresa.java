package co.edu.unal.reto8_devcompanies.data;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Modelo de una empresa del directorio (Reto 8).
 *
 * <p>Contiene los seis datos exigidos por el enunciado: nombre, URL,
 * teléfono, email, productos/servicios y una o varias clasificaciones.</p>
 */
public class Empresa {

    /** Valor usado cuando la empresa aún no está persistida en SQLite. */
    public static final long NO_ID = -1L;

    private long id;
    private String nombre;
    private String url;
    private String telefono;
    private String email;
    private String productosServicios;
    private final Set<Clasificacion> clasificaciones;

    public Empresa() {
        this.id = NO_ID;
        this.clasificaciones = EnumSet.noneOf(Clasificacion.class);
    }

    public Empresa(long id, String nombre, String url, String telefono,
                   String email, String productosServicios,
                   Set<Clasificacion> clasificaciones) {
        this.id = id;
        this.nombre = nombre;
        this.url = url;
        this.telefono = telefono;
        this.email = email;
        this.productosServicios = productosServicios;
        this.clasificaciones = (clasificaciones == null || clasificaciones.isEmpty())
                ? EnumSet.noneOf(Clasificacion.class)
                : EnumSet.copyOf(clasificaciones);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public boolean isPersisted() {
        return id != NO_ID;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getProductosServicios() {
        return productosServicios;
    }

    public void setProductosServicios(String productosServicios) {
        this.productosServicios = productosServicios;
    }

    public Set<Clasificacion> getClasificaciones() {
        return clasificaciones;
    }

    public void setClasificaciones(Set<Clasificacion> nuevas) {
        clasificaciones.clear();
        if (nuevas != null) {
            clasificaciones.addAll(nuevas);
        }
    }

    public boolean tiene(Clasificacion c) {
        return clasificaciones.contains(c);
    }

    /**
     * Serializa las clasificaciones a un TEXT con delimitadores en los
     * bordes, por ejemplo {@code ,CONSULTORIA,FABRICA_SOFTWARE,}.
     *
     * <p>Los delimitadores de borde permiten filtrar por coincidencia
     * exacta con {@code LIKE '%,ID,%'} sin falsos positivos entre
     * identificadores que comparten subcadena.</p>
     */
    @NonNull
    public String clasificacionesToStored() {
        if (clasificaciones.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(",");
        for (Clasificacion c : clasificaciones) {
            sb.append(c.getId()).append(",");
        }
        return sb.toString();
    }

    /** Reconstruye el conjunto de clasificaciones desde el TEXT almacenado. */
    public static Set<Clasificacion> clasificacionesFromStored(String stored) {
        Set<Clasificacion> result = EnumSet.noneOf(Clasificacion.class);
        if (stored == null || stored.trim().isEmpty()) {
            return result;
        }
        String[] parts = stored.split(",");
        for (String part : parts) {
            Clasificacion c = Clasificacion.fromId(part.trim());
            if (c != null) {
                result.add(c);
            }
        }
        return result;
    }

    /** Etiquetas legibles de las clasificaciones, en orden de declaración. */
    @NonNull
    public List<String> clasificacionLabels() {
        List<String> labels = new ArrayList<>();
        for (Clasificacion c : Clasificacion.values()) {
            if (clasificaciones.contains(c)) {
                labels.add(c.getLabel());
            }
        }
        return Collections.unmodifiableList(labels);
    }

    /** Clasificaciones en orden de declaración (para pintar chips). */
    @NonNull
    public List<Clasificacion> clasificacionesOrdenadas() {
        List<Clasificacion> ordered = new ArrayList<>();
        for (Clasificacion c : Clasificacion.values()) {
            if (clasificaciones.contains(c)) {
                ordered.add(c);
            }
        }
        return ordered;
    }

    @NonNull
    @Override
    public String toString() {
        return "Empresa{id=" + id + ", nombre='" + nombre + "', clasificaciones="
                + Arrays.toString(clasificacionesOrdenadas().toArray()) + "}";
    }
}
