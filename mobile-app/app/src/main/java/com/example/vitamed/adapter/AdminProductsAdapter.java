package com.example.vitamed.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.model.Product;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminProductsAdapter extends RecyclerView.Adapter<AdminProductsAdapter.VH> {

    public interface OnProductAction {
        void onEdit(Product p);
        void onDelete(Product p);
    }

    private final Context ctx;
    private final OnProductAction listener;

    // Full list + visible list
    private final List<Product> fullList = new ArrayList<>();
    private final List<Product> data = new ArrayList<>();

    private final NumberFormat nf = NumberFormat.getInstance(new Locale("en", "LB"));

    public AdminProductsAdapter(Context ctx, OnProductAction l) {
        this.ctx = ctx;
        this.listener = l;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx)
                .inflate(R.layout.item_admin_product, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int i) {
        Product p = data.get(i);

        h.tvName.setText(p.name == null ? "-" : p.name);

        String brand  = (p.brand == null || p.brand.isEmpty()) ? "—" : p.brand;
        String dosage = (p.dosage == null || p.dosage.isEmpty()) ? "—" : p.dosage;
        h.tvBrandDosage.setText(brand + " • " + dosage);

        h.tvPrice.setText(nf.format(p.price) + " LBP");
        h.tvAvailability.setText("In stock: " + p.availability);

        h.btnEdit.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(p);
        });

        h.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(p);
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public void replaceAll(List<Product> list) {
        fullList.clear();
        data.clear();

        if (list != null) {
            fullList.addAll(list);
            data.addAll(list);
        }

        notifyDataSetChanged();
    }

    // FILTER METHOD
    public void filter(String text) {
        data.clear();

        if (text == null || text.trim().isEmpty()) {
            data.addAll(fullList);
        } else {
            String q = text.toLowerCase().trim();

            for (Product p : fullList) {
                if (p.name != null && p.name.toLowerCase().contains(q)) {
                    data.add(p);
                }
            }
        }

        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvBrandDosage, tvPrice, tvAvailability;
        ImageView btnEdit, btnDelete;

        VH(@NonNull View v) {
            super(v);
            tvName         = v.findViewById(R.id.tvName);
            tvBrandDosage  = v.findViewById(R.id.tvBrandDosage);
            tvPrice        = v.findViewById(R.id.tvPrice);
            tvAvailability = v.findViewById(R.id.tvAvailability);
            btnEdit        = v.findViewById(R.id.btnEdit);
            btnDelete      = v.findViewById(R.id.btnDelete);
        }
    }
}
