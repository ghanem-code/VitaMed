package com.example.vitamed.ui.order;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.adapter.ChatAdapter;
import com.example.vitamed.model.ChatMessage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomerChatActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private String roomId = null;

    private EditText msgInput;
    private Button sendBtn;

    private RecyclerView messagesList;
    private ChatAdapter chatAdapter;

    private ListenerRegistration messagesListener = null;

    // ✅ prevents double send
    private boolean sending = false;

    // ✅ prevents seen loop spam
    private boolean markingSeen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_chat);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        msgInput = findViewById(R.id.msgInput);
        sendBtn = findViewById(R.id.sendBtn);

        messagesList = findViewById(R.id.messagesList);

        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true); // ✅ آخر الرسائل تحت
        messagesList.setLayoutManager(lm);

        chatAdapter = new ChatAdapter();
        messagesList.setAdapter(chatAdapter);

        if (auth.getCurrentUser() == null) {
            finish();
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        findOrCreateRoom(uid);

        sendBtn.setOnClickListener(v -> sendMessage(uid));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesListener != null) messagesListener.remove();
    }

    /* ================= FIND OR CREATE ROOM ================= */
    private void findOrCreateRoom(String uid) {

        db.collection("chat_rooms")
                .whereEqualTo("admin_id", "admin")
                .whereEqualTo("user_id", uid)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {

                    if (!snap.isEmpty()) {
                        for (QueryDocumentSnapshot d : snap) {
                            roomId = d.getId();
                            break;
                        }
                        listenMessagesRealTime();
                    } else {

                        Map<String, Object> room = new HashMap<>();
                        room.put("admin_id", "admin");
                        room.put("user_id", uid);
                        room.put("status", "open");
                        room.put("created_at", FieldValue.serverTimestamp());

                        db.collection("chat_rooms")
                                .add(room)
                                .addOnSuccessListener(ref -> {
                                    roomId = ref.getId();
                                    listenMessagesRealTime();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(this, "Failed create room: " + e.getMessage(), Toast.LENGTH_LONG).show()
                                );
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed load room: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }

    /* ================= SEND MESSAGE ================= */
    private void sendMessage(String uid) {

        if (sending) return;

        String text = msgInput.getText().toString().trim();
        if (text.isEmpty()) return;
        if (roomId == null) return;

        sending = true;
        sendBtn.setEnabled(false);

        Map<String, Object> msg = new HashMap<>();
        msg.put("room_id", roomId);
        msg.put("sender_id", uid);
        msg.put("sender_role", "user");
        msg.put("text", text);
        msg.put("seen", false);
        msg.put("created_at", FieldValue.serverTimestamp());

        db.collection("chat_messages")
                .add(msg)
                .addOnSuccessListener(r -> {
                    msgInput.setText("");
                    sending = false;
                    sendBtn.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Send failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    sending = false;
                    sendBtn.setEnabled(true);
                });
    }

    /* ================= LISTEN MESSAGES (REAL TIME) ================= */
    private void listenMessagesRealTime() {

        if (roomId == null) return;

        // ✅ remove old listener
        if (messagesListener != null) messagesListener.remove();

        Query q = db.collection("chat_messages")
                .whereEqualTo("room_id", roomId)
                .orderBy("created_at", Query.Direction.ASCENDING);

        messagesListener = q.addSnapshotListener((snap, e) -> {
            if (snap == null) return;

            List<ChatMessage> list = new ArrayList<>();
            snap.getDocuments().forEach(d -> {
                ChatMessage m = d.toObject(ChatMessage.class);
                if (m != null) list.add(m);
            });

            chatAdapter.setMessages(list);

            if (!list.isEmpty()) {
                messagesList.scrollToPosition(list.size() - 1);
            }

            // ✅ mark admin messages as seen (safe)
            markAdminMessagesAsSeen();
        });
    }

    /* ================= MARK ADMIN MSG AS SEEN (BATCH) ================= */
    private void markAdminMessagesAsSeen() {

        if (roomId == null) return;
        if (markingSeen) return;

        markingSeen = true;

        db.collection("chat_messages")
                .whereEqualTo("room_id", roomId)
                .whereEqualTo("sender_role", "admin")
                .whereEqualTo("seen", false)
                .get()
                .addOnSuccessListener(snap -> {

                    if (snap.isEmpty()) {
                        markingSeen = false;
                        return;
                    }

                    WriteBatch batch = db.batch();
                    snap.getDocuments().forEach(d ->
                            batch.update(d.getReference(), "seen", true)
                    );

                    batch.commit()
                            .addOnSuccessListener(x -> markingSeen = false)
                            .addOnFailureListener(err -> markingSeen = false);
                })
                .addOnFailureListener(err -> markingSeen = false);
    }
}
