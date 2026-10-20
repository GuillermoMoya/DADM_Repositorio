package co.edu.unal.reto8_devcompanies.data;

/**
 * Clasificaciones posibles de una empresa de desarrollo de software,
 * según el enunciado del Reto 8: consultoría, desarrollo a la medida
 * y/o fábrica de software.
 *
 * <p>El identificador ({@link #id}) es el valor estable que se persiste
 * en SQLite. La etiqueta ({@link #label}) es el texto legible para el
 * usuario. Una empresa puede tener más de una clasificación.</p>
 */
public enum Clasificacion {
    CONSULTORIA("CONSULTORIA", "Consultoría"),
    DESARROLLO_MEDIDA("DESARROLLO_MEDIDA", "Desarrollo a la medida"),
    FABRICA_SOFTWARE("FABRICA_SOFTWARE", "Fábrica de software");

    private final String id;
    private final String label;

    Clasificacion(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Devuelve la clasificación cuyo identificador coincide, o {@code null}
     * si ninguno coincide. Útil al leer valores persistidos.
     */
    public static Clasificacion fromId(String id) {
        if (id == null) {
            return null;
        }
        for (Clasificacion c : values()) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }
}
