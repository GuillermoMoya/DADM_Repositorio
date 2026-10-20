package co.edu.unal.reto8_devcompanies;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import co.edu.unal.reto8_devcompanies.data.Clasificacion;
import co.edu.unal.reto8_devcompanies.data.Empresa;
import co.edu.unal.reto8_devcompanies.data.EmpresaDao;
import co.edu.unal.reto8_devcompanies.ui.EmpresaAdapter;

/**
 * Pantalla única del directorio de empresas (Reto 8).
 *
 * <p>Combina búsqueda por nombre, filtro por clasificación, listado en
 * RecyclerView, formulario de alta/edición en diálogo y confirmación de
 * borrado. Toda la persistencia pasa por {@link EmpresaDao} (SQLite).</p>
 */
public class MainActivity extends AppCompatActivity
        implements EmpresaAdapter.OnEmpresaAction {

    private EmpresaDao dao;
    private EmpresaAdapter adapter;

    private EditText searchInput;
    private Spinner filterSpinner;
    private View emptyState;
    private RecyclerView recycler;

    /** Opciones del spinner de filtro: "Todas" + una por clasificación. */
    private final List<Clasificacion> filtroOpciones = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dao = new EmpresaDao(this);

        searchInput = findViewById(R.id.searchInput);
        filterSpinner = findViewById(R.id.filterSpinner);
        emptyState = findViewById(R.id.emptyState);
        recycler = findViewById(R.id.recycler);

        adapter = new EmpresaAdapter(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        setupFilterSpinner();
        setupSearch();

        findViewById(R.id.fabAdd).setOnClickListener(v -> showForm(null));

        reload();
    }

    // ---------------------------------------------------------- filtros / UI

    private void setupFilterSpinner() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.filter_all));
        filtroOpciones.add(null); // "Todas"
        for (Clasificacion c : Clasificacion.values()) {
            labels.add(c.getLabel());
            filtroOpciones.add(c);
        }

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filterSpinner.setAdapter(spinnerAdapter);

        filterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                reload();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // sin acción
            }
        });
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                reload();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /** Relee la base aplicando el filtro combinado nombre + clasificación. */
    private void reload() {
        String nombre = searchInput.getText() == null
                ? null : searchInput.getText().toString();
        Clasificacion clasificacion = selectedClasificacion();

        List<Empresa> empresas = dao.filter(nombre, clasificacion);
        adapter.submit(empresas);

        boolean vacio = empresas.isEmpty();
        emptyState.setVisibility(vacio ? View.VISIBLE : View.GONE);
        recycler.setVisibility(vacio ? View.GONE : View.VISIBLE);
    }

    @Nullable
    private Clasificacion selectedClasificacion() {
        int pos = filterSpinner.getSelectedItemPosition();
        if (pos < 0 || pos >= filtroOpciones.size()) {
            return null;
        }
        return filtroOpciones.get(pos);
    }

    // ------------------------------------------------- acciones del adapter

    @Override
    public void onEdit(@NonNull Empresa empresa) {
        showForm(empresa);
    }

    @Override
    public void onDelete(@NonNull Empresa empresa) {
        confirmDelete(empresa);
    }

    // ------------------------------------------------------- CRUD + diálogos

    /** Muestra el formulario para crear (empresa null) o editar. */
    private void showForm(@Nullable Empresa existente) {
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_empresa_form, null, false);

        TextInputEditText inNombre = form.findViewById(R.id.inputNombre);
        TextInputEditText inUrl = form.findViewById(R.id.inputUrl);
        TextInputEditText inTelefono = form.findViewById(R.id.inputTelefono);
        TextInputEditText inEmail = form.findViewById(R.id.inputEmail);
        TextInputEditText inProductos = form.findViewById(R.id.inputProductos);
        CheckBox chkConsultoria = form.findViewById(R.id.checkConsultoria);
        CheckBox chkMedida = form.findViewById(R.id.checkDesarrolloMedida);
        CheckBox chkFabrica = form.findViewById(R.id.checkFabricaSoftware);

        boolean editando = existente != null;
        if (editando) {
            inNombre.setText(existente.getNombre());
            inUrl.setText(existente.getUrl());
            inTelefono.setText(existente.getTelefono());
            inEmail.setText(existente.getEmail());
            inProductos.setText(existente.getProductosServicios());
            chkConsultoria.setChecked(existente.tiene(Clasificacion.CONSULTORIA));
            chkMedida.setChecked(existente.tiene(Clasificacion.DESARROLLO_MEDIDA));
            chkFabrica.setChecked(existente.tiene(Clasificacion.FABRICA_SOFTWARE));
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editando ? R.string.form_edit_title : R.string.form_new_title)
                .setView(form)
                .setPositiveButton(editando ? R.string.update : R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.show();

        // Interceptar el botón positivo para validar sin cerrar si hay error.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nombre = text(inNombre);
            if (nombre.isEmpty()) {
                inNombre.setError(getString(R.string.error_name_required));
                inNombre.requestFocus();
                return;
            }

            Set<Clasificacion> clasificaciones = EnumSet.noneOf(Clasificacion.class);
            if (chkConsultoria.isChecked()) clasificaciones.add(Clasificacion.CONSULTORIA);
            if (chkMedida.isChecked()) clasificaciones.add(Clasificacion.DESARROLLO_MEDIDA);
            if (chkFabrica.isChecked()) clasificaciones.add(Clasificacion.FABRICA_SOFTWARE);

            Empresa empresa = editando ? existente : new Empresa();
            empresa.setNombre(nombre);
            empresa.setUrl(text(inUrl));
            empresa.setTelefono(text(inTelefono));
            empresa.setEmail(text(inEmail));
            empresa.setProductosServicios(text(inProductos));
            empresa.setClasificaciones(clasificaciones);

            if (editando) {
                dao.update(empresa);
                toast(getString(R.string.updated_ok));
            } else {
                dao.insert(empresa);
                toast(getString(R.string.created_ok));
            }

            dialog.dismiss();
            reload();
        });
    }

    /** Pide confirmación antes de borrar (requisito explícito del reto). */
    private void confirmDelete(@NonNull Empresa empresa) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(getString(R.string.delete_confirm_message, empresa.getNombre()))
                .setPositiveButton(R.string.delete, (d, which) -> {
                    dao.delete(empresa.getId());
                    toast(getString(R.string.deleted_ok));
                    reload();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------- helpers

    @NonNull
    private String text(@Nullable EditText field) {
        if (field == null || field.getText() == null) {
            return "";
        }
        return field.getText().toString().trim();
    }

    private void toast(@NonNull String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
