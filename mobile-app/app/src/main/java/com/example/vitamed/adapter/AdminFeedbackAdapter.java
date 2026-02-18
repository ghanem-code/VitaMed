package com.example.vitamed.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.model.Feedback;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AdminFeedbackAdapter extends RecyclerView.Adapter<AdminFeedbackAdapter.VH> {

    private final List<Feedback> data = new ArrayList<>();
    private final SimpleDateFormat sdf =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_feedback, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Feedback f = data.get(position);

        // Pharmacy name
        h.tvPharmacy.setText(
                f.pharmacyname != null && !f.pharmacyname.isEmpty()
                        ? f.pharmacyname
                        : "Unknown pharmacy"
        );

        // Date
        if (f.created_at != null) {
            h.tvDate.setText(sdf.format(f.created_at.toDate()));
        } else {
            h.tvDate.setText("-");
        }

        // Message
        h.tvMessage.setText(f.message != null ? f.message : "");

        // User id
        h.tvUserId.setText("User: " + (f.user_id != null ? f.user_id : "-"));

        // Rating
        h.tvRating.setText("Rating: " + f.rating + "/5");


    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public void replaceAll(List<Feedback> list) {
        data.clear();
        data.addAll(list);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvPharmacy, tvDate, tvMessage, tvUserId, tvRating, tvHasImage;

        VH(@NonNull View v) {
            super(v);
            tvPharmacy = v.findViewById(R.id.tvPharmacy);
            tvDate     = v.findViewById(R.id.tvDate);
            tvMessage  = v.findViewById(R.id.tvMessage);
            tvUserId   = v.findViewById(R.id.tvUserId);
            tvRating   = v.findViewById(R.id.tvRating);

        }
    }
}
