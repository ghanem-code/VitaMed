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

public class OrderItemsAdapter extends RecyclerView.Adapter<OrderItemsAdapter.VH> {

    private final List<OrderItem> data = new ArrayList<>();
    private final NumberFormat nf = NumberFormat.getInstance(new Locale("en", "LB"));

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order_line, parent, false); // ✅ تأكد نفس اسم XML
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {

        OrderItem it = data.get(position);

        // ✅ safe setText (حتى لو null)
        h.tvName.setText(it.name != null ? it.name : "Item");
        h.tvQty.setText("Qty: " + it.qty);

        long price = it.unit_price;
        long lineTotal = (long) it.qty * it.unit_price;

        h.tvPrice.setText("Price: " + nf.format(price) + " LBP");
        h.tvTotal.setText("Total: " + nf.format(lineTotal) + " LBP");
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public void replaceAll(List<OrderItem> list) {
        data.clear();
        if (list != null) data.addAll(list);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {

        TextView tvName, tvQty, tvPrice, tvTotal;

        VH(@NonNull View v) {
            super(v);

            tvName  = v.findViewById(R.id.tvName);
            tvQty   = v.findViewById(R.id.tvQty);
            tvPrice = v.findViewById(R.id.tvPrice);
            tvTotal = v.findViewById(R.id.tvTotal);
        }
    }
}
