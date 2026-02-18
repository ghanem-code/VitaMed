package com.example.vitamed.ui.order;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.adapter.ProductAdapter;
import com.example.vitamed.model.Product;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderActivity extends AppCompatActivity {

    private RecyclerView rv;
    private ProductAdapter adapter;
    private EditText etSearch;

    // ✅ Firestore
    private FirebaseFirestore db;

    // ✅ Real-time listener
    private ListenerRegistration productsListener = null;

    // ✅ keep search after realtime updates
    private String currentSearch = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order);

        db = FirebaseFirestore.getInstance();

        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);
        tb.setNavigationOnClickListener(v -> finish());

        rv = findViewById(R.id.rvProducts);
        etSearch = findViewById(R.id.etSearch);

        adapter = new ProductAdapter(); // نفس الأدابتر
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        // ✅ REAL TIME PRODUCTS
        listenProductsRealTime();

        // ✅ بحث فوري
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearch = s.toString();
                adapter.filter(currentSearch);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        findViewById(R.id.btnPlaceOrder).setOnClickListener(v -> placeOrder());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (productsListener != null) productsListener.remove();
    }

    /* ================= REAL TIME PRODUCTS ================= */
    private void listenProductsRealTime() {

        // ✅ stop old listener if exists
        if (productsListener != null) productsListener.remove();

        productsListener = db.collection("products")
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {

                    if (e != null) {
                        Toast.makeText(this, "Realtime error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (snap == null) return;

                    List<Product> list = new ArrayList<>();

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Product p = d.toObject(Product.class);
                        if (p != null) {
                            p.docId = d.getId();
                            list.add(p);
                        }
                    }

                    // ✅ update adapter
                    adapter.replaceAll(list);

                    // ✅ keep search filter after realtime update
                    if (currentSearch != null && !currentSearch.trim().isEmpty()) {
                        adapter.filter(currentSearch);
                    }
                });
    }

    /* ================= PLACE ORDER ================= */
    private void placeOrder() {

        List<ProductAdapter.CartLine> cart = adapter.getCart();

        if (cart.isEmpty()) {
            Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }

        db.runTransaction((Transaction.Function<Void>) transaction -> {

            long total = 0L;

            // =========================
            // 1) Read products + validate stock
            // =========================
            class UpdateLine {
                DocumentReference prodRef;
                long newAvail;

                String productDocId;
                String name;
                String brand;
                String dosage;

                long unitPrice;
                int qty;

                UpdateLine(DocumentReference r,
                           long nAvail,
                           String pid,
                           String nm,
                           String br,
                           String dos,
                           long up,
                           int q) {
                    prodRef = r;
                    newAvail = nAvail;
                    productDocId = pid;
                    name = nm;
                    brand = br;
                    dosage = dos;
                    unitPrice = up;
                    qty = q;
                }
            }

            List<UpdateLine> updates = new ArrayList<>();

            for (ProductAdapter.CartLine line : cart) {

                if (line.qty <= 0) {
                    throw new FirebaseFirestoreException("Invalid qty for " + line.name,
                            FirebaseFirestoreException.Code.ABORTED);
                }

                DocumentReference prodRef = db.collection("products").document(line.productDocId);

                DocumentSnapshot snap = transaction.get(prodRef);

                Long avail = snap.getLong("availability");
                if (avail == null) avail = 0L;

                if (avail < line.qty) {
                    throw new FirebaseFirestoreException(line.name + " not enough stock",
                            FirebaseFirestoreException.Code.ABORTED);
                }

                long newAvail = avail - line.qty;

                total += (line.unitPrice * line.qty);

                String brand = (line.brand != null) ? line.brand : "";
                String dosage = (line.dosage != null) ? line.dosage : "";

                updates.add(new UpdateLine(
                        prodRef,
                        newAvail,
                        line.productDocId,
                        line.name,
                        brand,
                        dosage,
                        line.unitPrice,
                        line.qty
                ));
            }

            // =========================
            // 2) Invoice/Order number from counter
            // =========================
            DocumentReference counterRef = db.collection("meta").document("order_counter");
            DocumentSnapshot counterSnap = transaction.get(counterRef);

            long nextNumber;

            if (counterSnap.exists() && counterSnap.getLong("next") != null) {
                nextNumber = counterSnap.getLong("next");
                transaction.update(counterRef, "next", nextNumber + 1);
            } else {
                nextNumber = 1001L;
                Map<String, Object> init = new HashMap<>();
                init.put("next", nextNumber + 1);
                transaction.set(counterRef, init);
            }

            // =========================
            // 3) Update products stock
            // =========================
            for (UpdateLine u : updates) {
                transaction.update(u.prodRef, "availability", u.newAvail);
            }

            // =========================
            // 4) Create ORDER document
            // =========================
            DocumentReference orderRef = db.collection("orders").document();

            Map<String, Object> orderDoc = new HashMap<>();
            orderDoc.put("id", orderRef.getId());
            orderDoc.put("number", nextNumber);
            orderDoc.put("user_id", uid);
            orderDoc.put("total", total);
            orderDoc.put("status", "completed");
            orderDoc.put("created_at", FieldValue.serverTimestamp());

            transaction.set(orderRef, orderDoc);

            // =========================
            // 5) Create ORDER ITEMS documents
            // =========================
            for (UpdateLine u : updates) {

                DocumentReference itemRef = db.collection("order_items").document();

                Map<String, Object> m = new HashMap<>();
                m.put("id", itemRef.getId());
                m.put("order_id", orderRef.getId());
                m.put("product_id", u.productDocId);

                m.put("name", u.name);
                m.put("brand", u.brand);
                m.put("dosage", u.dosage);

                m.put("qty", u.qty);
                m.put("unit_price", u.unitPrice);
                m.put("line_total", u.unitPrice * u.qty);

                transaction.set(itemRef, m);
            }

            // =========================
            // 6) Create INVOICE document ✅
            // =========================
            DocumentReference invoiceRef = db.collection("invoices").document();

            Map<String, Object> invoiceDoc = new HashMap<>();
            invoiceDoc.put("id", invoiceRef.getId());
            invoiceDoc.put("number", nextNumber);
            invoiceDoc.put("order_id", orderRef.getId());
            invoiceDoc.put("user_id", uid);

            invoiceDoc.put("original_total", total);
            invoiceDoc.put("updated_total", total);
            invoiceDoc.put("return_total", 0);

            invoiceDoc.put("status", "pending");
            invoiceDoc.put("created_at", FieldValue.serverTimestamp());

            transaction.set(invoiceRef, invoiceDoc);

            return null;

        }).addOnSuccessListener(x -> {
            Toast.makeText(this, "Order placed successfully ✅", Toast.LENGTH_LONG).show();
            adapter.clearQuantities();

            // ✅ optional: clear search after order
            // etSearch.setText("");
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }
}
