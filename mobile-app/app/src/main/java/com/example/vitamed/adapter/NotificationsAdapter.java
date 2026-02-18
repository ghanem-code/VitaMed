package com.example.vitamed.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.model.NotificationItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.VH> {

    private final List<NotificationItem> data = new ArrayList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        NotificationItem n = data.get(pos);

        h.tvType.setText(n.type.toUpperCase());
        h.tvMessage.setText(n.message);

        if (n.timestamp != null)
            h.tvDate.setText(sdf.format(n.timestamp.toDate()));
        else
            h.tvDate.setText("-");
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void replaceAll(List<NotificationItem> list) {
        data.clear();
        data.addAll(list);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvMessage, tvType, tvDate;

        VH(@NonNull View v) {
            super(v);
            tvMessage = v.findViewById(R.id.tvNotifMessage);
            tvType = v.findViewById(R.id.tvNotifType);
            tvDate = v.findViewById(R.id.tvNotifDate);
        }
    }
}
