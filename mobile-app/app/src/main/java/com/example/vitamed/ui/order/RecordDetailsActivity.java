package com.example.vitamed.ui.order;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.adapter.OrderItemsAdapter;
import com.example.vitamed.model.Invoice;
import com.example.vitamed.model.OrderItem;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class RecordDetailsActivity extends AppCompatActivity {

    private OrderItemsAdapter adapter;
    private final NumberFormat nf = NumberFormat.getInstance(new Locale("en", "LB"));

    private FirebaseFirestore db;

    private ListenerRegistration itemsListener = null;
    private ListenerRegistration invoiceListener = null;

    private TextView tvGrandTotal;

    private String invoiceId;
    private String orderId;
    private long invNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record_details);

        db = FirebaseFirestore.getInstance();

        invoiceId = getIntent().getStringExtra("invoice_id");
        orderId   = getIntent().getStringExtra("order_id");
        invNumber = getIntent().getLongExtra("invoice_number", 0L);

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        tb.setNavigationOnClickListener(v -> finish());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Invoice #" + String.format(Locale.getDefault(), "%06d", invNumber));
        }

        tvGrandTotal = findViewById(R.id.tvGrandTotal);

        RecyclerView rv = findViewById(R.id.rvLines);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OrderItemsAdapter();
        rv.setAdapter(adapter);

        // ✅ REAL TIME
        listenItemsLive();
        listenInvoiceLive();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (itemsListener != null) itemsListener.remove();
        if (invoiceListener != null) invoiceListener.remove();
    }

    private void listenItemsLive() {

        if (orderId == null || orderId.isEmpty()) return;

        if (itemsListener != null) itemsListener.remove();

        itemsListener = db.collection("order_items")
                .whereEqualTo("order_id", orderId)
                // ✅ removed orderBy to avoid index error
                .addSnapshotListener((snap, e) -> {

                    if (e != null || snap == null) return;

                    List<OrderItem> items = new ArrayList<>();

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        OrderItem it = d.toObject(OrderItem.class);
                        if (it != null) {
                            items.add(it);
                        }
                    }

                    // ✅ ترتيب بالـ Java حسب الاسم
                    Collections.sort(items, new Comparator<OrderItem>() {
                        @Override
                        public int compare(OrderItem a, OrderItem b) {
                            String na = (a.name != null) ? a.name : "";
                            String nb = (b.name != null) ? b.name : "";
                            return na.compareToIgnoreCase(nb);
                        }
                    });

                    adapter.replaceAll(items);
                });
    }

    private void listenInvoiceLive() {

        if (invoiceId == null || invoiceId.isEmpty()) {
            tvGrandTotal.setText("Total: -");
            return;
        }

        if (invoiceListener != null) invoiceListener.remove();

        invoiceListener = db.collection("invoices")
                .document(invoiceId)
                .addSnapshotListener((doc, e) -> {

                    if (e != null || doc == null) return;

                    if (!doc.exists()) {
                        tvGrandTotal.setText("Total: (Invoice deleted)");
                        return;
                    }

                    Invoice inv = doc.toObject(Invoice.class);
                    if (inv == null) return;

                    long total = inv.updated_total;
                    tvGrandTotal.setText("Total: " + nf.format(total) + " LBP");
                });
    }
}
