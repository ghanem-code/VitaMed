package com.example.vitamed.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.adapter.AdminProductsAdapter;
import com.example.vitamed.model.Product;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminProductsActivity extends AppCompatActivity {

    private RecyclerView rv;
    private AdminProductsAdapter adapter;
    private FloatingActionButton btnAdd;
    private SearchView searchView;

    private FirebaseFirestore db;

    // ✅ real-time listener
    private ListenerRegistration productsListener = null;

    // ✅ keep search after realtime updates
    private String currentSearch = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_products);

        db = FirebaseFirestore.getInstance();

        Toolbar tb = findViewById(R.id.toolbarAdmin);
        setSupportActionBar(tb);
        tb.setNavigationOnClickListener(v -> finish());

        searchView = findViewById(R.id.searchView);
        rv = findViewById(R.id.rvAdminProducts);
        btnAdd = findViewById(R.id.btnAddProduct);

        adapter = new AdminProductsAdapter(this, new AdminProductsAdapter.OnProductAction() {
            @Override
            public void onEdit(Product p) {
                showEditDialog(p);
            }

            @Override
            public void onDelete(Product p) {
                deleteProduct(p);
            }
        });

        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        // 🔍 SEARCH LISTENER
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentSearch = query != null ? query : "";
                adapter.filter(currentSearch);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentSearch = newText != null ? newText : "";
                adapter.filter(currentSearch);
                return true;
            }
        });

        btnAdd.setOnClickListener(v -> showAddDialog());

        // ✅ REAL TIME
        listenProductsRealTime();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (productsListener != null) productsListener.remove();
    }

    /* ================= REAL TIME PRODUCTS ================= */
    private void listenProductsRealTime() {

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

                    adapter.replaceAll(list);

                    // ✅ keep search filter working
                    if (currentSearch != null && !currentSearch.trim().isEmpty()) {
                        adapter.filter(currentSearch);
                    }
                });
    }

    /* ================= ADMIN ACTIVITY LOG ================= */
    private void logAdminAction(String type, String message, String productId) {
        Map<String, Object> log = new HashMap<>();
        log.put("id", System.currentTimeMillis());
        log.put("type", type);
        log.put("message", message);
        log.put("productId", productId);
        log.put("timestamp", Timestamp.now());

        db.collection("admin_activity").add(log);
    }

    /* ================= ADD PRODUCT ================= */
    private void showAddDialog() {

        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_edit_product, null, false);

        TextInputLayout tilName  = view.findViewById(R.id.tilName);
        TextInputLayout tilBrand = view.findViewById(R.id.tilBrand);
        TextInputLayout tilDosage = view.findViewById(R.id.tilDosage);
        TextInputLayout tilPrice = view.findViewById(R.id.tilPrice);
        TextInputLayout tilAvail = view.findViewById(R.id.tilAvailability);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Add Product")
                .setView(view)
                .setPositiveButton("Save", (d, w) -> {

                    String name  = getText(tilName);
                    String brand = getText(tilBrand);
                    String dosage = getText(tilDosage);
                    String priceStr = getText(tilPrice);
                    String availStr = getText(tilAvail);

                    if (name.isEmpty()) {
                        Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    long price = 0;
                    long avail = 0;

                    try {
                        price = priceStr.isEmpty() ? 0 : Long.parseLong(priceStr);
                        avail = availStr.isEmpty() ? 0 : Long.parseLong(availStr);
                    } catch (Exception ex) {
                        Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> m = new HashMap<>();
                    m.put("name", name);
                    m.put("brand", brand);
                    m.put("dosage", dosage);
                    m.put("price", price);
                    m.put("availability", avail);
                    m.put("id", System.currentTimeMillis()); // optional

                    DocumentReference ref = db.collection("products").document();

                    ref.set(m)
                            .addOnSuccessListener(x -> {
                                Toast.makeText(this, "Added ✅", Toast.LENGTH_SHORT).show();
                                logAdminAction("add", "Admin added " + name, ref.getId());
                                // ✅ NO loadProducts() — realtime will update automatically
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                            );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /* ================= EDIT PRODUCT ================= */
    private void showEditDialog(Product p) {

        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_edit_product, null, false);

        TextInputLayout tilName  = view.findViewById(R.id.tilName);
        TextInputLayout tilBrand = view.findViewById(R.id.tilBrand);
        TextInputLayout tilDosage = view.findViewById(R.id.tilDosage);
        TextInputLayout tilPrice = view.findViewById(R.id.tilPrice);
        TextInputLayout tilAvail = view.findViewById(R.id.tilAvailability);

        setText(tilName, p.name);
        setText(tilBrand, p.brand);
        setText(tilDosage, p.dosage);
        setText(tilPrice, String.valueOf(p.price));
        setText(tilAvail, String.valueOf(p.availability));

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Edit Product")
                .setView(view)
                .setPositiveButton("Save", (d, w) -> {

                    String name  = getText(tilName);
                    String brand = getText(tilBrand);
                    String dosage = getText(tilDosage);
                    String priceStr = getText(tilPrice);
                    String availStr = getText(tilAvail);

                    if (name.isEmpty()) {
                        Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    long price = 0;
                    long avail = 0;

                    try {
                        price = priceStr.isEmpty() ? 0 : Long.parseLong(priceStr);
                        avail = availStr.isEmpty() ? 0 : Long.parseLong(availStr);
                    } catch (Exception ex) {
                        Toast.makeText(this, "Invalid number", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Map<String, Object> m = new HashMap<>();
                    m.put("name", name);
                    m.put("brand", brand);
                    m.put("dosage", dosage);
                    m.put("price", price);
                    m.put("availability", avail);

                    db.collection("products")
                            .document(p.docId)
                            .update(m)
                            .addOnSuccessListener(x -> {
                                Toast.makeText(this, "Updated ✅", Toast.LENGTH_SHORT).show();
                                logAdminAction("edit", "Admin updated " + name, p.docId);
                                // ✅ NO loadProducts() — realtime will update automatically
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                            );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /* ================= DELETE PRODUCT ================= */
    private void deleteProduct(Product p) {

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete product")
                .setMessage("Delete " + p.name + "?")
                .setPositiveButton("Delete", (d, w) -> {

                    db.collection("products")
                            .document(p.docId)
                            .delete()
                            .addOnSuccessListener(x -> {
                                Toast.makeText(this, "Deleted ✅", Toast.LENGTH_SHORT).show();
                                logAdminAction("delete", "Admin deleted " + p.name, p.docId);
                                // ✅ NO loadProducts() — realtime will update automatically
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                            );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /* ================= HELPERS ================= */

    private String getText(TextInputLayout til) {
        EditText et = til.getEditText();
        return et == null ? "" : et.getText().toString().trim();
    }

    private void setText(TextInputLayout til, String s) {
        EditText et = til.getEditText();
        if (et != null) et.setText(s == null ? "" : s);
    }
}
