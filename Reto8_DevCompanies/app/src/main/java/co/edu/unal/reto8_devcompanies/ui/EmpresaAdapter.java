package co.edu.unal.reto8_devcompanies.ui;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import co.edu.unal.reto8_devcompanies.R;
import co.edu.unal.reto8_devcompanies.data.Clasificacion;
import co.edu.unal.reto8_devcompanies.data.Empresa;

/**
 * Adaptador del RecyclerView que pinta cada empresa como una tarjeta con
 * sus datos de contacto y chips de clasificación. Expone callbacks para
 * editar y eliminar.
 */
public class EmpresaAdapter extends RecyclerView.Adapter<EmpresaAdapter.EmpresaViewHolder> {

    /** Callback de acciones sobre una empresa concreta. */
    public interface OnEmpresaAction {
        void onEdit(@NonNull Empresa empresa);
        void onDelete(@NonNull Empresa empresa);
    }

    private final List<Empresa> items = new ArrayList<>();
    private final OnEmpresaAction listener;

    public EmpresaAdapter(@NonNull OnEmpresaAction listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<Empresa> nuevos) {
        items.clear();
        items.addAll(nuevos);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EmpresaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_empresa, parent, false);
        return new EmpresaViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull EmpresaViewHolder holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class EmpresaViewHolder extends RecyclerView.ViewHolder {

        private final TextView nombre;
        private final TextView productos;
        private final TextView contacto;
        private final TextView url;
        private final LinearLayout chips;
        private final ImageButton btnEdit;
        private final ImageButton btnDelete;

        EmpresaViewHolder(@NonNull View itemView) {
            super(itemView);
            nombre = itemView.findViewById(R.id.itemNombre);
            productos = itemView.findViewById(R.id.itemProductos);
            contacto = itemView.findViewById(R.id.itemContacto);
            url = itemView.findViewById(R.id.itemUrl);
            chips = itemView.findViewById(R.id.itemChips);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        void bind(@NonNull Empresa e, @NonNull OnEmpresaAction listener) {
            nombre.setText(e.getNombre());

            setOrHide(productos, e.getProductosServicios());
            setOrHide(url, e.getUrl());

            // Contacto = email · teléfono (lo que haya disponible)
            List<String> partesContacto = new ArrayList<>();
            if (!isBlank(e.getEmail())) {
                partesContacto.add(e.getEmail());
            }
            if (!isBlank(e.getTelefono())) {
                partesContacto.add(e.getTelefono());
            }
            setOrHide(contacto, TextUtils.join("  ·  ", partesContacto));

            renderChips(e);

            btnEdit.setOnClickListener(v -> listener.onEdit(e));
            btnDelete.setOnClickListener(v -> listener.onDelete(e));
        }

        private void renderChips(@NonNull Empresa e) {
            chips.removeAllViews();
            for (Clasificacion c : e.clasificacionesOrdenadas()) {
                chips.addView(buildChip(c));
            }
            chips.setVisibility(e.getClasificaciones().isEmpty() ? View.GONE : View.VISIBLE);
        }

        private TextView buildChip(@NonNull Clasificacion c) {
            int bgColor;
            int textColor;
            switch (c) {
                case CONSULTORIA:
                    bgColor = R.color.chip_consultoria_bg;
                    textColor = R.color.chip_consultoria_text;
                    break;
                case DESARROLLO_MEDIDA:
                    bgColor = R.color.chip_medida_bg;
                    textColor = R.color.chip_medida_text;
                    break;
                case FABRICA_SOFTWARE:
                default:
                    bgColor = R.color.chip_fabrica_bg;
                    textColor = R.color.chip_fabrica_text;
                    break;
            }

            TextView chip = new TextView(itemView.getContext());
            chip.setText(c.getLabel());
            chip.setTextSize(12f);
            chip.setTextColor(ContextCompat.getColor(itemView.getContext(), textColor));
            chip.setPadding(dp(12), dp(5), dp(12), dp(5));

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(16));
            bg.setColor(ContextCompat.getColor(itemView.getContext(), bgColor));
            chip.setBackground(bg);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(8), 0);
            chip.setLayoutParams(lp);
            return chip;
        }

        private int dp(int value) {
            float density = itemView.getResources().getDisplayMetrics().density;
            return Math.round(value * density);
        }

        private void setOrHide(@NonNull TextView view, String text) {
            if (isBlank(text)) {
                view.setVisibility(View.GONE);
            } else {
                view.setVisibility(View.VISIBLE);
                view.setText(text);
            }
        }

        private boolean isBlank(String s) {
            return s == null || s.trim().isEmpty();
        }
    }
}
