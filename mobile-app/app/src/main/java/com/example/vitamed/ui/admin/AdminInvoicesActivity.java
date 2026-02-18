package com.example.vitamed.ui.admin;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.adapter.AdminInvoicesAdapter;
import com.example.vitamed.model.Invoice;
import com.example.vitamed.model.InvoiceItem;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class AdminInvoicesActivity extends AppCompatActivity {

    private RecyclerView rv;
    private AdminInvoicesAdapter adapter;
    private FirebaseFirestore db;

    // ✅ Real-time listener for invoices
    private ListenerRegistration invoicesListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_invoices);

        db = FirebaseFirestore.getInstance();

        Toolbar tb = findViewById(R.id.toolbarInvoices);
        setSupportActionBar(tb);
        tb.setNavigationOnClickListener(v -> finish());

        rv = findViewById(R.id.rvInvoices);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AdminInvoicesAdapter();
        rv.setAdapter(adapter);

        listenInvoicesRealTime(); // ✅ بدل loadInvoices()
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (invoicesListener != null) invoicesListener.remove();
    }

    /* ================= REAL TIME INVOICES ================= */
    private void listenInvoicesRealTime() {

        // ✅ remove old listener
        if (invoicesListener != null) invoicesListener.remove();

        invoicesListener = db.collection("invoices")
                .orderBy("created_at", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {

                    if (e != null) {
                        Toast.makeText(this, "Realtime error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (snap == null) return;

                    List<Invoice> list = new ArrayList<>();

                    for (DocumentSnapshot d : snap.getDocuments()) {

                        Invoice invoice = d.toObject(Invoice.class);
                        if (invoice == null) continue;

                        invoice.id = d.getId();
                        list.add(invoice);

                        // ✅ load extra details (name + items)
                        loadPharmacy(invoice);
                        loadItems(invoice);
                    }

                    adapter.replaceAll(list);
                });
    }

    /* ================= LOAD PHARMACY NAME ================= */
    private void loadPharmacy(Invoice invoice) {

        if (invoice.user_id == null || invoice.user_id.isEmpty()) {
            invoice.pharmacyname = "Unknown Pharmacy";
            adapter.notifyDataSetChanged();
            return;
        }

        db.collection("users")
                .document(invoice.user_id)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("pharmacyname");
                        invoice.pharmacyname = (name != null && !name.isEmpty())
                                ? name
                                : "Unknown Pharmacy";
                    } else {
                        invoice.pharmacyname = "Unknown Pharmacy";
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(err -> {
                    invoice.pharmacyname = "Unknown Pharmacy";
                    adapter.notifyDataSetChanged();
                });
    }

    /* ================= LOAD ORDER ITEMS ================= */
    private void loadItems(Invoice invoice) {

        if (invoice.order_id == null || invoice.order_id.isEmpty()) {
            adapter.removeById(invoice.id);
            return;
        }

        db.collection("order_items")
                .whereEqualTo("order_id", invoice.order_id)
                .get()
                .addOnSuccessListener(q -> {

                    List<InvoiceItem> items = new ArrayList<>();

                    for (DocumentSnapshot d : q.getDocuments()) {
                        InvoiceItem item = d.toObject(InvoiceItem.class);
                        if (item != null) items.add(item);
                    }

                    if (items.isEmpty()) {
                        adapter.removeById(invoice.id);
                    } else {
                        invoice.items = items;
                        adapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(err ->
                        Toast.makeText(this,
                                "Failed to load items: " + err.getMessage(),
                                Toast.LENGTH_SHORT).show()
                );
    }
}
