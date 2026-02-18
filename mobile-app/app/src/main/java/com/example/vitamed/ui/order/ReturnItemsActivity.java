package com.example.vitamed.ui.order;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.adapter.ReturnItemsAdapter;
import com.example.vitamed.model.OrderItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReturnItemsActivity extends AppCompatActivity {

    private RecyclerView rv;
    private ReturnItemsAdapter adapter;

    private String orderId;
    private String invoiceId;
    private long invoiceNumber;

    private String userId;

    private TextView tvInvoiceTotal;
    private TextView tvStatusNote; // ✅ from your XML

    private long currentTotal = 0L;

    private FirebaseFirestore db;

    // ✅ realtime listeners
    private ListenerRegistration itemsListener = null;
    private ListenerRegistration orderListener = null;
    private ListenerRegistration invoiceListener = null;

    private boolean returnLocked = false; // ✅ lock submit & + -

    private final NumberFormat nf = NumberFormat.getInstance(new Locale("en", "LB"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_return_items);

        db = FirebaseFirestore.getInstance();

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        tb.setNavigationOnClickListener(v -> finish());

        rv = findViewById(R.id.rvItems);
        rv.setLayoutManager(new LinearLayoutManager(this));

        tvInvoiceTotal = findViewById(R.id.tvInvoiceTotal);
        tvStatusNote = findViewById(R.id.tvStatusNote);

        adapter = new ReturnItemsAdapter();
        rv.setAdapter(adapter);

        orderId = getIntent().getStringExtra("order_id");
        invoiceId = getIntent().getStringExtra("invoice_id");
        invoiceNumber = getIntent().getLongExtra("invoice_number", 0L);

        userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (orderId == null || orderId.isEmpty()) {
            Toast.makeText(this, "Missing order id", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ✅ REAL TIME
        listenInvoiceStatusLive(); // ✅ COMPLETED lock here
        listenOrderTotalLive();
        listenOrderItemsLive();

        findViewById(R.id.btnSubmitReturn).setOnClickListener(v -> submitReturn());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (itemsListener != null) itemsListener.remove();
        if (orderListener != null) orderListener.remove();
        if (invoiceListener != null) invoiceListener.remove();
    }

    /* ================= INVOICE STATUS (COMPLETED LOCK) ================= */
    private void listenInvoiceStatusLive() {

        if (invoiceId == null || invoiceId.isEmpty()) {
            // إذا ما عندك invoiceId ما منقفل شي
            return;
        }

        if (invoiceListener != null) invoiceListener.remove();

        invoiceListener = db.collection("invoices")
                .document(invoiceId)
                .addSnapshotListener((doc, e) -> {

                    if (doc == null || !doc.exists()) return;

                    String status = doc.getString("status");
                    String s = status == null ? "" : status.toLowerCase();

                    if (s.equals("completed")) {

                        returnLocked = true;

                        if (tvStatusNote != null) {
                            tvStatusNote.setVisibility(View.VISIBLE);
                            tvStatusNote.setText("✅ Completed — return disabled");
                        }

                        // ✅ lock submit button
                        View submit = findViewById(R.id.btnSubmitReturn);
                        submit.setEnabled(false);
                        submit.setAlpha(0.35f);

                        // ✅ lock + -
                        adapter.setReturnLocked(true);

                    } else {

                        returnLocked = false;

                        if (tvStatusNote != null) {
                            tvStatusNote.setVisibility(View.GONE);
                        }

                        View submit = findViewById(R.id.btnSubmitReturn);
                        submit.setEnabled(true);
                        submit.setAlpha(1f);

                        adapter.setReturnLocked(false);
                    }
                });
    }

    /* ================= REAL TIME ORDER TOTAL ================= */
    private void listenOrderTotalLive() {

        if (orderListener != null) orderListener.remove();

        orderListener = db.collection("orders")
                .document(orderId)
                .addSnapshotListener((doc, e) -> {

                    if (doc == null || !doc.exists()) {
                        currentTotal = 0L;
                        updateTotalUI();
                        return;
                    }

                    Long t = doc.getLong("total");
                    currentTotal = (t != null) ? t : 0L;
                    updateTotalUI();
                });
    }

    private void updateTotalUI() {
        tvInvoiceTotal.setText("Total: " + nf.format(currentTotal) + " LBP");
    }

    /* ================= REAL TIME ORDER ITEMS ================= */
    private void listenOrderItemsLive() {

        if (itemsListener != null) itemsListener.remove();

        Query q = db.collection("order_items")
                .whereEqualTo("order_id", orderId);

        itemsListener = q.addSnapshotListener((snap, e) -> {

            if (snap == null) return;

            List<OrderItem> list = new ArrayList<>();

            for (DocumentSnapshot d : snap.getDocuments()) {
                OrderItem item = d.toObject(OrderItem.class);
                if (item != null) {
                    item.id = d.getId(); // ✅ important
                    list.add(item);
                }
            }

            adapter.replaceAll(list);
        });
    }

    /* ================= SUBMIT RETURN (TRANSACTION) ================= */
    private void submitReturn() {

        // ✅ UI lock safety
        if (returnLocked) {
            Toast.makeText(this,
                    "Return disabled: invoice is completed ✅",
                    Toast.LENGTH_LONG).show();
            return;
        }

        List<OrderItem> items = adapter.getItems();
        if (items == null || items.isEmpty()) {
            Toast.makeText(this, "No items found", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean hasReturn = false;
        for (OrderItem it : items) {
            if (it.returnQty > 0) {
                hasReturn = true;
                break;
            }
        }
        if (!hasReturn) {
            Toast.makeText(this, "Choose return quantities first", Toast.LENGTH_SHORT).show();
            return;
        }

        final long[] newTotalHolder = new long[1];

        db.runTransaction((Transaction.Function<Void>) transaction -> {

            // ✅ Firestore lock safety (web-style)
            if (invoiceId != null && !invoiceId.isEmpty()) {
                DocumentReference invRef = db.collection("invoices").document(invoiceId);
                DocumentSnapshot invSnap = transaction.get(invRef);

                String statusNow = invSnap.getString("status");
                String s = statusNow == null ? "" : statusNow.toLowerCase();

                if (s.equals("completed")) {
                    throw new RuntimeException("Return disabled: invoice is completed ✅");
                }
            }

            long updatedTotal = 0L;

            for (OrderItem item : items) {

                int oldQty = item.qty;
                int returnQty = item.returnQty;

                if (returnQty < 0) returnQty = 0;
                if (returnQty > oldQty) returnQty = oldQty;

                int newQty = oldQty - returnQty;

                long newLine = (long) newQty * item.unit_price;
                updatedTotal += newLine;

                if (returnQty > 0) {

                    // 1) restore product availability
                    if (item.product_id != null && !item.product_id.isEmpty()) {

                        DocumentReference prodRef =
                                db.collection("products").document(item.product_id);

                        DocumentSnapshot prodSnap = transaction.get(prodRef);
                        Long avail = prodSnap.getLong("availability");
                        if (avail == null) avail = 0L;

                        long newAvail = avail + returnQty;
                        transaction.update(prodRef, "availability", newAvail);
                    }

                    // 2) update order_item qty + line_total
                    if (item.id != null && !item.id.isEmpty()) {

                        DocumentReference itemRef =
                                db.collection("order_items").document(item.id);

                        Map<String, Object> updateItem = new HashMap<>();
                        updateItem.put("qty", newQty);
                        updateItem.put("line_total", newLine);

                        transaction.update(itemRef, updateItem);
                    }
                }
            }

            // ✅ update order total
            DocumentReference orderRef = db.collection("orders").document(orderId);
            transaction.update(orderRef, "total", updatedTotal);

            newTotalHolder[0] = updatedTotal;
            return null;

        }).addOnSuccessListener(r -> {

            long newTotal = newTotalHolder[0];

            if (newTotal == 0) {
                deleteInvoiceCompletely();
                return;
            }

            Toast.makeText(this,
                    "Return applied successfully ✅",
                    Toast.LENGTH_LONG).show();

        }).addOnFailureListener(e ->
                Toast.makeText(this, "Return failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
        );
    }

    /* ================= DELETE EVERYTHING WHEN TOTAL=0 ================= */
    private void deleteInvoiceCompletely() {

        if (itemsListener != null) itemsListener.remove();
        if (orderListener != null) orderListener.remove();
        if (invoiceListener != null) invoiceListener.remove();

        db.collection("order_items")
                .whereEqualTo("order_id", orderId)
                .get()
                .addOnSuccessListener(snap -> {

                    WriteBatch batch = db.batch();

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        batch.delete(d.getReference());
                    }

                    if (invoiceId != null && !invoiceId.isEmpty()) {
                        batch.delete(db.collection("invoices").document(invoiceId));
                    }

                    db.collection("invoices")
                            .whereEqualTo("order_id", orderId)
                            .get()
                            .addOnSuccessListener(invSnap -> {

                                for (DocumentSnapshot inv : invSnap.getDocuments()) {
                                    batch.delete(inv.getReference());
                                }

                                batch.delete(db.collection("orders").document(orderId));

                                batch.commit()
                                        .addOnSuccessListener(x -> {
                                            Toast.makeText(this,
                                                    "Invoice fully returned & deleted ✅",
                                                    Toast.LENGTH_LONG).show();
                                            finish();
                                        })
                                        .addOnFailureListener(e ->
                                                Toast.makeText(this,
                                                        "Delete failed: " + e.getMessage(),
                                                        Toast.LENGTH_LONG).show()
                                        );
                            });
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Delete failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
    }
}
