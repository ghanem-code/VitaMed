package com.example.vitamed.ui.price;

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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class PriceActivity extends AppCompatActivity {

    private RecyclerView rv;
    private ProductAdapter adapter;
    private EditText etSearch;

    private FirebaseFirestore db;
    private ListenerRegistration productsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_price);

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
        listenProductsLive();

        // ✅ بحث فوري
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void listenProductsLive() {

        // ✅ remove old listener if any
        if (productsListener != null) productsListener.remove();

        productsListener = db.collection("products")
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {

                    if (e != null) {
                        Toast.makeText(this, "Realtime error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snap == null) return;

                    List<Product> list = new ArrayList<>();

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Product p = d.toObject(Product.class);
                        if (p != null) {
                            p.docId = d.getId();
                            p.qty = 0; // ✅ Price page فقط
                            list.add(p);
                        }
                    }

                    adapter.replaceAll(list);

                    // ✅ إذا المستخدم كاتب شي بالبحث، خلي الفلتر شغال مباشرة
                    String txt = etSearch.getText().toString();
                    if (!txt.isEmpty()) adapter.filter(txt);
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (productsListener != null) productsListener.remove();
    }
}
