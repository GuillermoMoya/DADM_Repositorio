package co.edu.unal.reto8_devcompanies.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.NonNull;

/**
 * {@link SQLiteOpenHelper} del directorio de empresas (Reto 8).
 *
 * <p>Define la tabla {@code empresas} con un id autoincremental y los seis
 * campos exigidos por el enunciado. No usa Room ni dependencias externas,
 * solo el framework de SQLite de Android.</p>
 */
public class EmpresaDbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "devcompanies.db";
    public static final int DB_VERSION = 1;

    public static final String TABLE = "empresas";
    public static final String COL_ID = "id";
    public static final String COL_NOMBRE = "nombre";
    public static final String COL_URL = "url";
    public static final String COL_TELEFONO = "telefono";
    public static final String COL_EMAIL = "email";
    public static final String COL_PRODUCTOS = "productos_servicios";
    public static final String COL_CLASIFICACIONES = "clasificaciones";

    private static final String SQL_CREATE =
            "CREATE TABLE " + TABLE + " (" +
                    COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_NOMBRE + " TEXT NOT NULL, " +
                    COL_URL + " TEXT, " +
                    COL_TELEFONO + " TEXT, " +
                    COL_EMAIL + " TEXT, " +
                    COL_PRODUCTOS + " TEXT, " +
                    COL_CLASIFICACIONES + " TEXT" +
                    ");";

    private static final String SQL_DROP = "DROP TABLE IF EXISTS " + TABLE + ";";

    public EmpresaDbHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        db.execSQL(SQL_CREATE);
        seed(db);
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // Prototipo del reto: recrear la tabla es suficiente.
        db.execSQL(SQL_DROP);
        onCreate(db);
    }

    /** Carga unas pocas empresas de ejemplo para que la demo no arranque vacía. */
    private void seed(@NonNull SQLiteDatabase db) {
        insertSeed(db, "Andes Software", "https://andes.example",
                "+57 1 555 0101", "contacto@andes.example",
                "Desarrollo de aplicaciones empresariales a la medida.",
                ",DESARROLLO_MEDIDA,CONSULTORIA,");
        insertSeed(db, "QuantumByte", "https://quantumbyte.example",
                "+57 2 555 0147", "hola@quantumbyte.example",
                "Fábrica de software y outsourcing de equipos.",
                ",FABRICA_SOFTWARE,");
        insertSeed(db, "NimbusLab", "https://nimbuslab.example",
                "+57 4 555 0199", "ventas@nimbuslab.example",
                "Consultoría en arquitectura cloud y DevOps.",
                ",CONSULTORIA,");
    }

    private void insertSeed(SQLiteDatabase db, String nombre, String url,
                            String telefono, String email, String productos,
                            String clasificaciones) {
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(COL_NOMBRE, nombre);
        v.put(COL_URL, url);
        v.put(COL_TELEFONO, telefono);
        v.put(COL_EMAIL, email);
        v.put(COL_PRODUCTOS, productos);
        v.put(COL_CLASIFICACIONES, clasificaciones);
        db.insert(TABLE, null, v);
    }
}
