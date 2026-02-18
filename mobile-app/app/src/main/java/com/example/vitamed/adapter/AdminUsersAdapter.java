package com.example.vitamed.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.model.User;

import java.util.ArrayList;
import java.util.List;

public class AdminUsersAdapter extends RecyclerView.Adapter<AdminUsersAdapter.VH> {

    public interface OnUserAction {
        void onToggleStatus(User u);
        void onDelete(User u);
    }

    private List<User> data = new ArrayList<>();
    private List<User> fullList = new ArrayList<>();
    private OnUserAction listener;

    public AdminUsersAdapter(OnUserAction l) {
        this.listener = l;
    }

    public void replaceAll(List<User> list) {
        fullList.clear();
        data.clear();

        if (list != null) {
            fullList.addAll(list);
            data.addAll(list);
        }
        notifyDataSetChanged();
    }

    // 🔍 Filtering by pharmacy name
    public void filter(String txt) {
        data.clear();

        if (txt == null || txt.trim().isEmpty()) {
            data.addAll(fullList);
        } else {
            String q = txt.toLowerCase().trim();

            for (User u : fullList) {
                if (u.pharmacyname != null &&
                        u.pharmacyname.toLowerCase().contains(q)) {
                    data.add(u);
                }
            }
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        User u = data.get(pos);

        h.tvName.setText(u.pharmacyname);
        h.tvEmail.setText(u.email);
        h.tvPhone.setText("Phone: " + u.phone);

        if (u.status) {
            h.btnStatus.setText("Deactivate");
            h.btnStatus.setBackgroundColor(0xFFD32F2F); // Red
        } else {
            h.btnStatus.setText("Activate");
            h.btnStatus.setBackgroundColor(0xFF388E3C); // Green
        }

        h.btnStatus.setOnClickListener(v -> listener.onToggleStatus(u));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(u));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvEmail, tvPhone, btnStatus, btnDelete;

        VH(View v) {
            super(v);
            tvName = v.findViewById(R.id.tvName);
            tvEmail = v.findViewById(R.id.tvEmail);
            tvPhone = v.findViewById(R.id.tvPhone);
            btnStatus = v.findViewById(R.id.btnBlockUnblock); // نفس الـ id
            btnDelete = v.findViewById(R.id.btnDelete);
        }
    }
}
