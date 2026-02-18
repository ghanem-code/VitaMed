package com.example.vitamed.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.model.Product;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_AVAILABLE = 1;
    private static final int TYPE_OUT_OF_STOCK = 2;

    private final List<Product> all = new ArrayList<>();
    private final List<Product> filtered = new ArrayList<>();

    private final NumberFormat nf = NumberFormat.getInstance(new Locale("en", "LB"));

    // ====== Cart Line ======
    public static class CartLine {
        public String productDocId;
        public String name;
        public String brand;   // ✅ add
        public String dosage;  // ✅ add
        public long unitPrice;
        public int qty;

        public CartLine(String id, String n, long p, int q) {
            productDocId = id;
            name = n;
            unitPrice = p;
            qty = q;
        }
    }

    @Override
    public int getItemViewType(int position) {
        Product p = filtered.get(position);
        if (p.availability <= 0) return TYPE_OUT_OF_STOCK;
        return TYPE_AVAILABLE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        if (viewType == TYPE_OUT_OF_STOCK) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_product2, parent, false);
            return new VHOut(v);
        } else {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_product, parent, false);
            return new VHAvailable(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int i) {

        Product p = filtered.get(i);

        if (holder instanceof VHOut) {
            VHOut h = (VHOut) holder;

            h.tvName.setText(p.name != null ? p.name : "-");
            h.tvBrand.setText(p.brand != null ? p.brand : "-");
            h.tvDosage.setText(p.dosage != null ? p.dosage : "-");
            h.tvPrice.setText(nf.format(p.price) + " LBP");

            // item_product2 أصلاً كاتب Out of stock جاهزة
            h.tvAvailability.setText("Out of stock");
        }

        else if (holder instanceof VHAvailable) {
            VHAvailable h = (VHAvailable) holder;

            h.tvName.setText(p.name != null ? p.name : "-");
            h.tvBrand.setText(p.brand != null ? p.brand : "-");
            h.tvDosage.setText(p.dosage != null ? p.dosage : "-");
            h.tvPrice.setText(nf.format(p.price) + " LBP");

            // Availability text
            h.tvAvailability.setText("Available: " + p.availability);

            // qty UI
            h.tvQty.setText(String.valueOf(p.qty));

            h.btnMinus.setOnClickListener(v -> {
                if (p.qty > 0) {
                    p.qty--;
                    notifyItemChanged(i);
                }
            });

            h.btnPlus.setOnClickListener(v -> {
                if (p.qty < p.availability) {
                    p.qty++;
                    notifyItemChanged(i);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return filtered.size();
    }

    // ✅ replaceAll
    public void replaceAll(List<Product> list) {
        all.clear();
        filtered.clear();

        for (Product p : list) {
            p.qty = 0; // reset qty
            all.add(p);
            filtered.add(p);
        }
        notifyDataSetChanged();
    }

    // ✅ search filter
    public void filter(String q) {
        String s = (q == null) ? "" : q.trim().toLowerCase();

        filtered.clear();
        if (s.isEmpty()) {
            filtered.addAll(all);
        } else {
            for (Product p : all) {
                String name = (p.name == null) ? "" : p.name.toLowerCase();
                String brand = (p.brand == null) ? "" : p.brand.toLowerCase();
                if (name.contains(s) || brand.contains(s)) {
                    filtered.add(p);
                }
            }
        }
        notifyDataSetChanged();
    }

    // ✅ cart data
    public List<CartLine> getCart() {
        List<CartLine> cart = new ArrayList<>();
        for (Product p : all) {
            if (p.qty > 0 && p.availability > 0) {
                cart.add(new CartLine(p.docId, p.name, p.price, p.qty));
            }
        }
        return cart;
    }

    public void clearQuantities() {
        for (Product p : all) p.qty = 0;
        notifyDataSetChanged();
    }

    // =======================
    // ViewHolders
    // =======================

    // ✅ Available holder (item_product.xml)
    static class VHAvailable extends RecyclerView.ViewHolder {

        TextView tvName, tvBrand, tvDosage, tvPrice, tvAvailability;
        TextView tvQty, btnMinus, btnPlus;

        VHAvailable(@NonNull View v) {
            super(v);

            tvName = v.findViewById(R.id.tvName);
            tvBrand = v.findViewById(R.id.tvBrand);
            tvDosage = v.findViewById(R.id.tvDosage);
            tvPrice = v.findViewById(R.id.tvPrice);
            tvAvailability = v.findViewById(R.id.tvAvailability);

            tvQty = v.findViewById(R.id.tvQty);
            btnMinus = v.findViewById(R.id.btnMinus);
            btnPlus = v.findViewById(R.id.btnPlus);
        }
    }

    // ✅ Out of stock holder (item_product2.xml)
    static class VHOut extends RecyclerView.ViewHolder {

        TextView tvName, tvBrand, tvDosage, tvPrice, tvAvailability;

        VHOut(@NonNull View v) {
            super(v);

            tvName = v.findViewById(R.id.tvName);
            tvBrand = v.findViewById(R.id.tvBrand);
            tvDosage = v.findViewById(R.id.tvDosage);
            tvPrice = v.findViewById(R.id.tvPrice);
            tvAvailability = v.findViewById(R.id.tvAvailability);
        }
    }
}
