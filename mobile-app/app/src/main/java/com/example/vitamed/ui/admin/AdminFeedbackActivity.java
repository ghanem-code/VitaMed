package com.example.vitamed.ui.admin;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.adapter.AdminFeedbackAdapter;
import com.example.vitamed.model.Feedback;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class AdminFeedbackActivity extends AppCompatActivity {

    private RecyclerView rv;
    private AdminFeedbackAdapter adapter;
    private FirebaseFirestore db;

    // ✅ Real-time listener
    private ListenerRegistration feedbackListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_feedback);

        db = FirebaseFirestore.getInstance();

        Toolbar tb = findViewById(R.id.toolbarFeedback);
        setSupportActionBar(tb);
        tb.setNavigationOnClickListener(v -> finish());

        rv = findViewById(R.id.rvFeedback);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AdminFeedbackAdapter();
        rv.setAdapter(adapter);

        listenFeedbackRealTime(); // ✅ بدل loadFeedback()
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // ✅ stop listener to avoid memory leak
        if (feedbackListener != null) feedbackListener.remove();
    }

    /* ================= REAL TIME FEEDBACK ================= */
    private void listenFeedbackRealTime() {

        // ✅ إذا في Listener قديم شيله
        if (feedbackListener != null) feedbackListener.remove();

        feedbackListener = db.collection("feedback")
                .orderBy("created_at", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {

                    if (e != null) {
                        Toast.makeText(this,
                                "Realtime failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (snap == null) return;

                    List<Feedback> list = new ArrayList<>();

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Feedback f = d.toObject(Feedback.class);
                        if (f != null) {
                            if (f.id == null || f.id.isEmpty()) f.id = d.getId();
                            list.add(f);
                        }
                    }

                    adapter.replaceAll(list);
                });
    }
}
