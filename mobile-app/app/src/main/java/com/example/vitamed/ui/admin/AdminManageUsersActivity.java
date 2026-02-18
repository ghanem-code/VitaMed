package com.example.vitamed.ui.admin;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.adapter.AdminUsersAdapter;
import com.example.vitamed.model.User;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class AdminManageUsersActivity extends AppCompatActivity {

    private RecyclerView rv;
    private AdminUsersAdapter adapter;
    private FirebaseFirestore db;

    private ListenerRegistration usersListener = null;
    private String currentSearch = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_manage_users);

        db = FirebaseFirestore.getInstance();

        Toolbar tb = findViewById(R.id.toolbarManageUsers);
        setSupportActionBar(tb);
        tb.setNavigationOnClickListener(v -> finish());

        rv = findViewById(R.id.rvUsers);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AdminUsersAdapter(new AdminUsersAdapter.OnUserAction() {
            @Override
            public void onToggleStatus(User u) {
                toggleStatus(u);
            }

            @Override
            public void onDelete(User u) {
                // ❌ مثل الويب: ما بدنا delete هون
                Toast.makeText(AdminManageUsersActivity.this, "Delete disabled ❌", Toast.LENGTH_SHORT).show();
            }
        });

        rv.setAdapter(adapter);

        // 🔍 SEARCH (same as web filter)
        SearchView searchView = findViewById(R.id.searchUsers);
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

        // ✅ REAL TIME
        listenPharmaciesRealTime();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usersListener != null) usersListener.remove();
    }

    /* ================= REAL TIME PHARMACIES ONLY ================= */
    private void listenPharmaciesRealTime() {

        if (usersListener != null) usersListener.remove();

        usersListener = db.collection("users")
                .orderBy("username", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, e) -> {

                    if (e != null) {
                        Toast.makeText(this, "Realtime error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (snap == null) return;

                    List<User> list = new ArrayList<>();

                    for (DocumentSnapshot d : snap.getDocuments()) {

                        User u = d.toObject(User.class);
                        if (u == null) continue;

                        u.uid = d.getId();

                        // ✅ نفس الويب: بس يلي عندهم pharmacyname
                        if (u.pharmacyname == null || u.pharmacyname.trim().isEmpty()) {
                            continue;
                        }

                        // ✅ status default true
                        // إذا عندك status null أحياناً
                        // خليها true
                        // (بس إذا بالـ model User عندك boolean status رح تكون false إذا null)
                        // فخلينا نضبطها:
                        if (!d.contains("status")) {
                            u.status = true;
                        }

                        list.add(u);
                    }

                    adapter.replaceAll(list);

                    // ✅ keep search active after updates
                    if (currentSearch != null && !currentSearch.trim().isEmpty()) {
                        adapter.filter(currentSearch);
                    }
                });
    }

    /* ================= BLOCK / UNBLOCK ================= */
    private void toggleStatus(User u) {

        if (u == null || u.uid == null) return;

        boolean newStatus = !u.status;

        db.collection("users")
                .document(u.uid)
                .update("status", newStatus)
                .addOnSuccessListener(x ->
                        Toast.makeText(
                                this,
                                newStatus ? "Activated ✅" : "Blocked ⛔",
                                Toast.LENGTH_SHORT
                        ).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }
}
