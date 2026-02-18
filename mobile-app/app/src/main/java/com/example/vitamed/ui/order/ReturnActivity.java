package com.example.vitamed.ui.order;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.adapter.InvoiceAdapter;
import com.example.vitamed.model.Invoice;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class ReturnActivity extends AppCompatActivity {

    private RecyclerView rv;
    private InvoiceAdapter adapter;
    private TextView tvEmpty;
    private String uid;

    private FirebaseFirestore db;
    private ListenerRegistration invoicesListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_return);

        db = FirebaseFirestore.getInstance();

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        tb.setNavigationOnClickListener(v -> finish());

        rv = findViewById(R.id.rvInvoices);
        tvEmpty = findViewById(R.id.tvEmpty);

        adapter = new InvoiceAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        adapter.setOnInvoiceClick(inv -> {
            Intent i = new Intent(this, ReturnItemsActivity.class);
            i.putExtra("invoice_id", inv.id);
            i.putExtra("order_id", inv.order_id);
            i.putExtra("invoice_number", inv.number);
            startActivity(i);
        });

        uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) {
            tvEmpty.setText("Please log in again");
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        loadInvoicesLive();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (invoicesListener != null) invoicesListener.remove();
    }

    private void loadInvoicesLive() {

        tvEmpty.setText("Loading…");
        tvEmpty.setVisibility(View.VISIBLE);

        // ✅ remove old listener if any
        if (invoicesListener != null) invoicesListener.remove();

        invoicesListener = db.collection("invoices")
                .whereEqualTo("user_id", uid)
                .addSnapshotListener((snap, e) -> {

                    if (snap == null) return;

                    List<Invoice> list = new ArrayList<>();

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Invoice inv = d.toObject(Invoice.class);
                        if (inv != null) {
                            inv.id = d.getId();

                            // ✅ show only invoices that can be returned
                            // (غير حسب نظامك إذا بتحب)
                            String s = inv.status == null ? "" : inv.status.toLowerCase();

                            // ممنوع return إذا already returned
                            if (s.equals("returned")) continue;

                            // ممكن تمنع return إذا completed (اختياري)
                            // if (s.equals("completed")) continue;

                            // ✅ hide empty invoices
                            if (inv.updated_total <= 0) continue;

                            list.add(inv);
                        }
                    }

                    // ✅ ترتيب: completed بالآخر + newest first
                    list.sort((a, b) -> {

                        String sa = (a.status == null) ? "" : a.status.toLowerCase();
                        String sb = (b.status == null) ? "" : b.status.toLowerCase();

                        int scoreA = sa.equals("completed") ? 2 : 1;
                        int scoreB = sb.equals("completed") ? 2 : 1;

                        if (scoreA != scoreB) return scoreA - scoreB;

                        if (a.created_at == null) return 1;
                        if (b.created_at == null) return -1;

                        return b.created_at.toDate().compareTo(a.created_at.toDate());
                    });

                    adapter.replaceAll(list);

                    if (list.isEmpty()) {
                        tvEmpty.setText("No invoices available for return.");
                        tvEmpty.setVisibility(View.VISIBLE);
                    } else {
                        tvEmpty.setVisibility(View.GONE);
                    }
                });
    }
}
