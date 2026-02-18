package com.example.vitamed.model;

import com.google.firebase.Timestamp;

public class ChatMessage {

    public String room_id;
    public String sender_id;
    public String sender_role;
    public String text;
    public boolean seen;              // 👁️ SEEN FLAG
    public Timestamp created_at;

    public ChatMessage() {
        // 🔴 Required empty constructor for Firestore
    }

    public boolean isFromAdmin() {
        return "admin".equals(sender_role);
    }
}
