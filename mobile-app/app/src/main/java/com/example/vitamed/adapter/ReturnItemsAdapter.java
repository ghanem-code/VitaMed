package com.example.vitamed.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.model.OrderItem;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReturnItemsAdapter extends RecyclerView.Adapter<ReturnItemsAdapter.VH> {

    private final List<OrderItem> data = new ArrayList<>();
    private final NumberFormat nf = NumberFormat.getInstance(new Locale("en", "LB"));

    // ✅ NEW: lock + -
    private boolean returnLocked = false;

    public void setReturnLocked(boolean locked) {
        this.returnLocked = locked;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_return_product, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int i) {

        OrderItem p = data.get(i);

        long lineTotal = (long) p.unit_price * p.qty;

        h.tvName.setText(p.name == null ? "" : p.name);
        h.tvUnitPrice.setText("Unit price: " + nf.format(p.unit_price) + " LBP");
        h.tvQtyBought.setText("Bought: " + p.qty);
        h.tvLineTotal.setText("Total: " + nf.format(lineTotal) + " LBP");

        // Return Qty
        h.tvQtyReturn.setText(String.valueOf(p.returnQty));

        // ✅ LOCK UI
        if (returnLocked) {
            h.btnMinus.setEnabled(false);
            h.btnPlus.setEnabled(false);
            h.btnMinus.setAlpha(0.3f);
            h.btnPlus.setAlpha(0.3f);
        } else {
            h.btnMinus.setEnabled(true);
            h.btnPlus.setEnabled(true);
            h.btnMinus.setAlpha(1f);
            h.btnPlus.setAlpha(1f);
        }

        // -------- Buttons --------
        h.btnMinus.setOnClickListener(v -> {
            if (returnLocked) return;

            if (p.returnQty > 0) {
                p.returnQty--;
                notifyItemChanged(i);
            }
        });

        h.btnPlus.setOnClickListener(v -> {
            if (returnLocked) return;

            if (p.returnQty < p.qty) {
                p.returnQty++;
                notifyItemChanged(i);
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public List<OrderItem> getItems() {
        return data;
    }

    public void replaceAll(List<OrderItem> list) {
        data.clear();
        for (OrderItem i : list) i.returnQty = 0; // reset return qty
        data.addAll(list);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {

        TextView tvName, tvUnitPrice, tvLineTotal, tvQtyBought, tvQtyReturn, btnMinus, btnPlus;

        VH(@NonNull View v) {
            super(v);
            tvName       = v.findViewById(R.id.tvName);
            tvUnitPrice  = v.findViewById(R.id.tvUnitPrice);
            tvLineTotal  = v.findViewById(R.id.tvLineTotal);
            tvQtyBought  = v.findViewById(R.id.tvQtyBought);
            tvQtyReturn  = v.findViewById(R.id.tvQtyReturn);
            btnMinus     = v.findViewById(R.id.btnMinus);
            btnPlus      = v.findViewById(R.id.btnPlus);
        }
    }
}
