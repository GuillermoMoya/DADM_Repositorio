package co.edu.unal.reto8_devcompanies.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Objeto de acceso a datos del directorio de empresas (Reto 8).
 *
 * <p>Implementa las operaciones CRUD y el listado con filtros por nombre
 * y/o clasificación. Todas las consultas usan argumentos parametrizados
 * para evitar inyección SQL y problemas de escape.</p>
 */
public class EmpresaDao {

    private final EmpresaDbHelper helper;

    public EmpresaDao(@NonNull Context context) {
        this.helper = new EmpresaDbHelper(context);
    }

    private static final String[] COLUMNS = {
            EmpresaDbHelper.COL_ID,
            EmpresaDbHelper.COL_NOMBRE,
            EmpresaDbHelper.COL_URL,
            EmpresaDbHelper.COL_TELEFONO,
            EmpresaDbHelper.COL_EMAIL,
            EmpresaDbHelper.COL_PRODUCTOS,
            EmpresaDbHelper.COL_CLASIFICACIONES
    };

    // ---------------------------------------------------------------- CREATE

    /**
     * Inserta una empresa y devuelve su id generado, o {@link Empresa#NO_ID}
     * si la inserción falla.
     */
    public long insert(@NonNull Empresa e) {
        SQLiteDatabase db = helper.getWritableDatabase();
        long id = db.insert(EmpresaDbHelper.TABLE, null, toValues(e));
        if (id != -1) {
            e.setId(id);
        }
        return id;
    }

    // ------------------------------------------------------------- RETRIEVE

    /** Devuelve una empresa por id, o {@code null} si no existe. */
    @Nullable
    public Empresa findById(long id) {
        SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(EmpresaDbHelper.TABLE, COLUMNS,
                EmpresaDbHelper.COL_ID + " = ?",
                new String[]{String.valueOf(id)},
                null, null, null)) {
            if (c.moveToFirst()) {
                return fromCursor(c);
            }
            return null;
        }
    }

    /** Lista todas las empresas ordenadas por nombre. */
    @NonNull
    public List<Empresa> findAll() {
        return query(null, null);
    }

    /**
     * Lista las empresas aplicando filtro por nombre y/o clasificación.
     *
     * <p>Ambos filtros son opcionales y se combinan con AND: si se pasan
     * los dos, solo coinciden las empresas que cumplen ambos criterios.</p>
     *
     * @param nombreFiltro texto parcial a buscar en el nombre (puede ser null/vacío)
     * @param clasificacion clasificación exacta a exigir (puede ser null = todas)
     */
    @NonNull
    public List<Empresa> filter(@Nullable String nombreFiltro,
                                @Nullable Clasificacion clasificacion) {
        List<String> clauses = new ArrayList<>();
        List<String> args = new ArrayList<>();

        if (nombreFiltro != null && !nombreFiltro.trim().isEmpty()) {
            clauses.add(EmpresaDbHelper.COL_NOMBRE + " LIKE ?");
            args.add("%" + nombreFiltro.trim() + "%");
        }

        if (clasificacion != null) {
            // Coincidencia exacta del identificador gracias a los
            // delimitadores de borde almacenados: ,ID,
            clauses.add(EmpresaDbHelper.COL_CLASIFICACIONES + " LIKE ?");
            args.add("%," + clasificacion.getId() + ",%");
        }

        String selection = clauses.isEmpty() ? null : TextUtils.join(" AND ", clauses);
        String[] selectionArgs = args.isEmpty() ? null : args.toArray(new String[0]);
        return query(selection, selectionArgs);
    }

    @NonNull
    private List<Empresa> query(@Nullable String selection, @Nullable String[] args) {
        List<Empresa> result = new ArrayList<>();
        SQLiteDatabase db = helper.getReadableDatabase();
        String orderBy = EmpresaDbHelper.COL_NOMBRE + " COLLATE NOCASE ASC";
        try (Cursor c = db.query(EmpresaDbHelper.TABLE, COLUMNS,
                selection, args, null, null, orderBy)) {
            while (c.moveToNext()) {
                result.add(fromCursor(c));
            }
        }
        return result;
    }

    // --------------------------------------------------------------- UPDATE

    /** Actualiza una empresa existente. Devuelve el número de filas afectadas. */
    public int update(@NonNull Empresa e) {
        SQLiteDatabase db = helper.getWritableDatabase();
        return db.update(EmpresaDbHelper.TABLE, toValues(e),
                EmpresaDbHelper.COL_ID + " = ?",
                new String[]{String.valueOf(e.getId())});
    }

    // --------------------------------------------------------------- DELETE

    /** Elimina una empresa por id. Devuelve el número de filas afectadas. */
    public int delete(long id) {
        SQLiteDatabase db = helper.getWritableDatabase();
        return db.delete(EmpresaDbHelper.TABLE,
                EmpresaDbHelper.COL_ID + " = ?",
                new String[]{String.valueOf(id)});
    }

    // --------------------------------------------------------------- mapping

    private ContentValues toValues(@NonNull Empresa e) {
        ContentValues v = new ContentValues();
        v.put(EmpresaDbHelper.COL_NOMBRE, trimToNull(e.getNombre()));
        v.put(EmpresaDbHelper.COL_URL, trimToNull(e.getUrl()));
        v.put(EmpresaDbHelper.COL_TELEFONO, trimToNull(e.getTelefono()));
        v.put(EmpresaDbHelper.COL_EMAIL, trimToNull(e.getEmail()));
        v.put(EmpresaDbHelper.COL_PRODUCTOS, trimToNull(e.getProductosServicios()));
        v.put(EmpresaDbHelper.COL_CLASIFICACIONES, e.clasificacionesToStored());
        return v;
    }

    @NonNull
    private Empresa fromCursor(@NonNull Cursor c) {
        Empresa e = new Empresa();
        e.setId(c.getLong(c.getColumnIndexOrThrow(EmpresaDbHelper.COL_ID)));
        e.setNombre(c.getString(c.getColumnIndexOrThrow(EmpresaDbHelper.COL_NOMBRE)));
        e.setUrl(c.getString(c.getColumnIndexOrThrow(EmpresaDbHelper.COL_URL)));
        e.setTelefono(c.getString(c.getColumnIndexOrThrow(EmpresaDbHelper.COL_TELEFONO)));
        e.setEmail(c.getString(c.getColumnIndexOrThrow(EmpresaDbHelper.COL_EMAIL)));
        e.setProductosServicios(c.getString(c.getColumnIndexOrThrow(EmpresaDbHelper.COL_PRODUCTOS)));
        String stored = c.getString(c.getColumnIndexOrThrow(EmpresaDbHelper.COL_CLASIFICACIONES));
        e.setClasificaciones(Empresa.clasificacionesFromStored(stored));
        return e;
    }

    @Nullable
    private String trimToNull(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
