package com.example.vitamed.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.model.Invoice;
import com.example.vitamed.model.InvoiceItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminInvoicesAdapter extends RecyclerView.Adapter<AdminInvoicesAdapter.VH> {

    private List<Invoice> data = new ArrayList<>();
    private final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_invoice, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {

        Invoice in = data.get(pos);

        // اسم الصيدلية
        h.tvPharmacyName.setText(
                in.pharmacyname != null ? in.pharmacyname : "Unknown Pharmacy"
        );

        // التاريخ
        if (in.created_at != null)
            h.tvInvoiceDate.setText(sdf.format(in.created_at.toDate()));
        else
            h.tvInvoiceDate.setText("-");

        // المجموع
        h.tvTotal.setText("Total: " + in.getFinalTotal() + " LBP");

        // العناصر
        StringBuilder sb = new StringBuilder();

        if (in.items != null && !in.items.isEmpty()) {

            for (InvoiceItem item : in.items) {

                sb.append("• ")
                        .append(item.name)       // ✅ الاسم الصحيح
                        .append(" x")
                        .append(item.qty)        // ✅ الكمية الصحيحة
                        .append(" @")
                        .append(item.unit_price) // ✅ السعر الصحيح
                        .append(" LBP\n");
            }

        } else {
            sb.append("No medicines found");
        }

        h.tvItems.setText(sb.toString());
    }

    public void removeById(String id) {
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).id.equals(id)) {
                data.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public void replaceAll(List<Invoice> list) {
        data = list;
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {

        TextView tvPharmacyName, tvInvoiceDate, tvTotal, tvItems;

        VH(View v) {
            super(v);
            tvPharmacyName = v.findViewById(R.id.tvPharmacyName);
            tvInvoiceDate  = v.findViewById(R.id.tvInvoiceDate);
            tvTotal        = v.findViewById(R.id.tvTotal);
            tvItems        = v.findViewById(R.id.tvItems);
        }
    }
}
