package com.example.vitamed.ui.settings;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vitamed.R;
import com.example.vitamed.ui.auth.LoginActivity;
import com.example.vitamed.ui.feedback.FeedbackActivity;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class SettingsActivity extends AppCompatActivity {

    private TextView tvPharmacyName;
    private LinearLayout btnEditName, rowEditLocation,
            rowChangePassword, rowDeleteAccount, rowFeedback;
    private ImageView btnBack;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String uid;

    // ✅ Real-time listener
    private ListenerRegistration profileListener;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_settings);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        uid = user.getUid();

        tvPharmacyName = findViewById(R.id.tvPharmacyName);
        btnEditName = findViewById(R.id.btnEditName);
        rowEditLocation = findViewById(R.id.rowEditLocation);
        rowChangePassword = findViewById(R.id.rowChangePassword);
        rowDeleteAccount = findViewById(R.id.rowDeleteAccount);
        rowFeedback = findViewById(R.id.rowFeedback);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());

        // ✅ REAL TIME PROFILE
        listenProfileLive();

        btnEditName.setOnClickListener(v -> editField("pharmacyname", "Pharmacy Name"));
        rowEditLocation.setOnClickListener(v -> editField("location", "Location"));
        rowChangePassword.setOnClickListener(v -> changePassword());
        rowDeleteAccount.setOnClickListener(v -> deleteAccount());
        rowFeedback.setOnClickListener(v ->
                startActivity(new Intent(this, FeedbackActivity.class)));
    }

    /* ================= REAL TIME PROFILE ================= */
    private void listenProfileLive() {

        // ✅ remove old listener if any
        if (profileListener != null) profileListener.remove();

        profileListener = db.collection("users")
                .document(uid)
                .addSnapshotListener((snap, e) -> {

                    if (e != null) {
                        Toast.makeText(this, "Realtime error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snap == null || !snap.exists()) {
                        tvPharmacyName.setText("Unknown");
                        return;
                    }

                    String name = snap.getString("pharmacyname");
                    if (name == null || name.trim().isEmpty()) name = "Unknown pharmacy";

                    tvPharmacyName.setText(name);
                });
    }

    /* ================= EDIT NAME / LOCATION ================= */
    private void editField(String field, String title) {
        EditText input = new EditText(this);
        input.setHint(title);

        new AlertDialog.Builder(this)
                .setTitle("Edit " + title)
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {

                    String val = input.getText().toString().trim();
                    if (val.isEmpty()) {
                        Toast.makeText(this, "Value cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    db.collection("users")
                            .document(uid)
                            .update(field, val)
                            .addOnSuccessListener(x ->
                                    Toast.makeText(this, "Updated ✅", Toast.LENGTH_SHORT).show()
                            )
                            .addOnFailureListener(err ->
                                    Toast.makeText(this, "Failed: " + err.getMessage(), Toast.LENGTH_LONG).show()
                            );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /* ================= CHANGE PASSWORD ================= */
    private void changePassword() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        EditText oldP = new EditText(this);
        oldP.setHint("Current password");

        EditText newP = new EditText(this);
        newP.setHint("New password");

        EditText confP = new EditText(this);
        confP.setHint("Confirm password");

        box.addView(oldP);
        box.addView(newP);
        box.addView(confP);

        new AlertDialog.Builder(this)
                .setTitle("Change Password")
                .setView(box)
                .setPositiveButton("Change", (d, w) -> {

                    String oldPass = oldP.getText().toString();
                    String newPass = newP.getText().toString();
                    String confirm = confP.getText().toString();

                    if (newPass.isEmpty() || confirm.isEmpty()) {
                        Toast.makeText(this, "Password cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!newPass.equals(confirm)) {
                        Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null || user.getEmail() == null) return;

                    user.reauthenticate(
                            EmailAuthProvider.getCredential(user.getEmail(), oldPass)
                    ).addOnSuccessListener(x ->
                            user.updatePassword(newPass)
                                    .addOnSuccessListener(z ->
                                            Toast.makeText(this, "Password updated ✅", Toast.LENGTH_SHORT).show()
                                    )
                                    .addOnFailureListener(err ->
                                            Toast.makeText(this, "Failed: " + err.getMessage(), Toast.LENGTH_LONG).show()
                                    )
                    ).addOnFailureListener(err ->
                            Toast.makeText(this, "Wrong current password", Toast.LENGTH_SHORT).show()
                    );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /* ================= DELETE ACCOUNT ================= */
    private void deleteAccount() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("This will permanently delete your account.")
                .setPositiveButton("Delete", (d, w) -> {

                    FirebaseUser user = auth.getCurrentUser();
                    if (user == null) return;

                    // ✅ stop listener before delete
                    if (profileListener != null) profileListener.remove();

                    db.collection("users")
                            .document(uid)
                            .delete()
                            .addOnSuccessListener(x ->
                                    user.delete()
                                            .addOnSuccessListener(z -> {
                                                startActivity(new Intent(this, LoginActivity.class));
                                                finish();
                                            })
                                            .addOnFailureListener(err ->
                                                    Toast.makeText(this, "Delete Auth failed: " + err.getMessage(), Toast.LENGTH_LONG).show()
                                            )
                            )
                            .addOnFailureListener(err ->
                                    Toast.makeText(this, "Delete Firestore failed: " + err.getMessage(), Toast.LENGTH_LONG).show()
                            );
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (profileListener != null) profileListener.remove();
    }
}
