package com.example.vitamed.ui.order;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class recordActivity extends AppCompatActivity {

    private RecyclerView rv;
    private InvoiceAdapter adapter;
    private TextView tvEmpty;
    private String uid;

    private FirebaseFirestore db;
    private ListenerRegistration invoicesListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);

        db = FirebaseFirestore.getInstance();

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        tb.setNavigationOnClickListener(v -> finish());

        rv = findViewById(R.id.rvOrders);
        tvEmpty = findViewById(R.id.tvEmpty);

        adapter = new InvoiceAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        adapter.setOnInvoiceClick(inv -> {
            Intent i = new Intent(this, RecordDetailsActivity.class);

            i.putExtra("invoice_id", inv.id);
            i.putExtra("order_id", inv.order_id);
            i.putExtra("invoice_number", inv.number);
            i.putExtra("invoice_total", inv.updated_total);

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

        if (invoicesListener != null) invoicesListener.remove();

        invoicesListener = db.collection("invoices")
                .whereEqualTo("user_id", uid)
                // ✅ removed orderBy to avoid Firestore index error
                .addSnapshotListener((snap, e) -> {

                    if (e != null) {
                        // ✅ ما نخرب ال UI بنص طويل
                        Toast.makeText(this,
                                "Firestore error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();

                        tvEmpty.setText("Failed to load records.");
                        tvEmpty.setVisibility(View.VISIBLE);
                        return;
                    }

                    if (snap == null) return;

                    List<Invoice> filtered = new ArrayList<>();

                    for (DocumentSnapshot d : snap.getDocuments()) {

                        Invoice inv = d.toObject(Invoice.class);
                        if (inv != null) {
                            inv.id = d.getId();

                            // ✅ نفس الويب: ما نعرض invoice إذا updated_total=0
                            if (inv.updated_total > 0) {
                                filtered.add(inv);
                            }
                        }
                    }

                    // ✅ ترتيب الأحدث أولاً بالـ Java
                    Collections.sort(filtered, new Comparator<Invoice>() {
                        @Override
                        public int compare(Invoice a, Invoice b) {
                            long ta = (a.created_at != null) ? a.created_at.toDate().getTime() : 0;
                            long tb = (b.created_at != null) ? b.created_at.toDate().getTime() : 0;
                            return Long.compare(tb, ta); // DESC
                        }
                    });

                    adapter.replaceAll(filtered);

                    if (filtered.isEmpty()) {
                        tvEmpty.setText("No invoices found.");
                        tvEmpty.setVisibility(View.VISIBLE);
                    } else {
                        tvEmpty.setVisibility(View.GONE);
                    }
                });
    }
}
