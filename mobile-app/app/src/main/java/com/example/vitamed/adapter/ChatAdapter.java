package com.example.vitamed.adapter;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {

    private final List<ChatMessage> list = new ArrayList<>();

    public void setMessages(List<ChatMessage> msgs) {
        list.clear();
        if (msgs != null) list.addAll(msgs);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {

        ChatMessage m = list.get(position);

        String messageText = (m.text == null) ? "" : m.text;
        h.msg.setText(messageText);

        if (m.isFromAdmin()) {
            // ✅ ADMIN (LEFT)
            h.container.setGravity(Gravity.START);
            h.bubble.setBackgroundResource(R.drawable.bg_msg_admin);

            // admin no seen
            h.tvSeen.setVisibility(View.GONE);

        } else {
            // ✅ USER (RIGHT)
            h.container.setGravity(Gravity.END);
            h.bubble.setBackgroundResource(R.drawable.bg_msg_user);

            // seen / delivered
            h.tvSeen.setVisibility(View.VISIBLE);
            h.tvSeen.setText(m.seen ? "Seen" : "Delivered");
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        TextView msg;
        TextView tvSeen;
        LinearLayout container;
        LinearLayout bubble;

        VH(View v) {
            super(v);
            msg = v.findViewById(R.id.tvMessage);
            tvSeen = v.findViewById(R.id.tvSeen);
            container = v.findViewById(R.id.container);
            bubble = v.findViewById(R.id.bubble);
        }
    }
}
