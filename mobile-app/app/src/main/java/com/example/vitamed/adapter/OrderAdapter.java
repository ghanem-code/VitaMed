package com.example.vitamed.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.model.Order;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.VH> {

    public interface OnOrderClick { void onClick(Order order); }
    private OnOrderClick onOrderClick;
    public void setOnOrderClick(OnOrderClick l) { this.onOrderClick = l; }

    private final List<Order> data = new ArrayList<>();
    private final NumberFormat nf = NumberFormat.getInstance(new Locale("en", "LB"));
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int i) {
        Order o = data.get(i);

        String label;
        if (o.number > 0) {
            // إذا بتحب تعرض طبيعي:
            // label = "Order #" + o.number;

            // أو بأصفار بادئة (6 خانات كمثال):
            label = "Order #" + fmt(o.number);
        } else {
            String shortId = (o.id != null && o.id.length() > 6) ? o.id.substring(0, 6) : (o.id != null ? o.id : "-");
            label = "Order #" + shortId;  // fallback للطلبات القديمة
        }
        h.tvOrderId.setText(label);

        String dateStr = (o.created_at != null) ? sdf.format(o.created_at.toDate()) : "-";
        h.tvDate.setText(dateStr);

        h.tvTotal.setText(nf.format(o.total) + " LBP");
        h.tvStatus.setText(o.status != null ? o.status : "-");

        h.itemView.setOnClickListener(v -> {
            if (onOrderClick != null) onOrderClick.onClick(o);
        });
    }

    @Override public int getItemCount() { return data.size(); }

    public void replaceAll(List<Order> list) {
        data.clear();
        data.addAll(list);
        notifyDataSetChanged();
    }

    // (اختياري) لتنسيق الرقم مع أصفار بادئة
    private String fmt(long n) {
        return String.format(Locale.getDefault(), "%06d", n);
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvDate, tvTotal, tvStatus;
        VH(@NonNull View v) {
            super(v);
            tvOrderId = v.findViewById(R.id.tvOrderId);
            tvDate    = v.findViewById(R.id.tvDate);
            tvTotal   = v.findViewById(R.id.tvTotal);
            tvStatus  = v.findViewById(R.id.tvStatus);
        }
    }
}
