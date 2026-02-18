package com.example.vitamed.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.model.Invoice;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InvoiceAdapter extends RecyclerView.Adapter<InvoiceAdapter.VH> {

    public interface OnInvoiceClick { void onClick(Invoice inv); }
    private OnInvoiceClick onInvoiceClick;
    public void setOnInvoiceClick(OnInvoiceClick l) { this.onInvoiceClick = l; }

    private final List<Invoice> data = new ArrayList<>();
    private final NumberFormat nf = NumberFormat.getInstance(new Locale("en", "LB"));
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int i) {

        Invoice inv = data.get(i);

        h.tvOrderId.setText("Invoice #" + inv.number);


        String dateStr = (inv.created_at != null) ? sdf.format(inv.created_at.toDate()) : "-";
        h.tvDate.setText(dateStr);

        long totalToShow = inv.updated_total; // نفس الويب
        h.tvTotal.setText(nf.format(totalToShow) + " LBP");

        h.tvStatus.setText(inv.status != null ? inv.status : "-");

        h.itemView.setOnClickListener(v -> {
            if (onInvoiceClick != null) onInvoiceClick.onClick(inv);
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

    public void replaceAll(List<Invoice> list) {
        data.clear();
        data.addAll(list);
        notifyDataSetChanged();
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
