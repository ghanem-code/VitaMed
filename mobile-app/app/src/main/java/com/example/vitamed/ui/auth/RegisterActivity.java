package com.example.vitamed.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vitamed.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText etUser, etEmail, etPass, etConfirm,
            etPhone, etPharmacy, etLocation;
    private Button btnRegister;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_register);

        etUser      = findViewById(R.id.etRegUsername);
        etEmail     = findViewById(R.id.etRegEmail);
        etPass      = findViewById(R.id.etRegPassword);
        etConfirm   = findViewById(R.id.etRegConfirm);
        etPhone     = findViewById(R.id.etRegPhone);
        etPharmacy  = findViewById(R.id.etRegPharmacy);
        etLocation  = findViewById(R.id.etRegLocation);
        btnRegister = findViewById(R.id.btnRegister);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        TextView tvHaveAccount = findViewById(R.id.tvHaveAccount);
        tvHaveAccount.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        btnRegister.setOnClickListener(v -> doRegister());
    }

    private void doRegister() {
        String u    = etUser.getText().toString().trim();
        String e    = etEmail.getText().toString().trim();
        String p    = etPass.getText().toString().trim();
        String c    = etConfirm.getText().toString().trim();
        String ph   = etPhone.getText().toString().trim();
        String phar = etPharmacy.getText().toString().trim();
        String loc  = etLocation.getText().toString().trim();

        if (!validate(u, e, p, c, ph, phar, loc)) return;

        // ✅ check username duplicate
        db.collection("users")
                .whereEqualTo("username", u)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        etUser.setError("Username already taken");
                        return;
                    }
                    createUser(u, e, p, ph, phar, loc);
                })
                .addOnFailureListener(err -> Toast.makeText(
                        this,
                        "Error: " + err.getMessage(),
                        Toast.LENGTH_LONG
                ).show());
    }

    private void createUser(String u, String e, String p,
                            String phone, String pharmacy, String location) {

        auth.createUserWithEmailAndPassword(e, p)
                .addOnSuccessListener(r -> {

                    String uid = r.getUser().getUid();

                    Map<String, Object> doc = new HashMap<>();
                    doc.put("uid", uid);
                    doc.put("username", u);
                    doc.put("email", e);
                    doc.put("phone", phone);
                    doc.put("pharmacyname", pharmacy);
                    doc.put("location", location);

                    doc.put("status", false);          // ⛔ pending admin approval
                    doc.put("emailVerified", false);   // ✅ like web
                    doc.put("createdAt", FieldValue.serverTimestamp());

                    // ✅ 1) Save user in Firestore first
                    db.collection("users").document(uid)
                            .set(doc)
                            .addOnSuccessListener(x -> {

                                // ✅ 2) Send email verification
                                r.getUser().sendEmailVerification()
                                        .addOnSuccessListener(vv -> {

                                            Toast.makeText(
                                                    this,
                                                    "✅ Account created!\n📩 Verification email sent.\n⏳ Waiting for admin approval.",
                                                    Toast.LENGTH_LONG
                                            ).show();

                                            // ✅ important: signOut after register
                                            auth.signOut();

                                            startActivity(new Intent(this, LoginActivity.class));
                                            finish();
                                        })
                                        .addOnFailureListener(err -> Toast.makeText(
                                                this,
                                                "❌ Failed to send verification email:\n" + err.getMessage(),
                                                Toast.LENGTH_LONG
                                        ).show());

                            })
                            .addOnFailureListener(err -> Toast.makeText(
                                    this,
                                    "❌ Failed saving user data:\n" + err.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show());

                })
                .addOnFailureListener(e2 -> Toast.makeText(
                        this,
                        "Register failed: " + e2.getMessage(),
                        Toast.LENGTH_LONG
                ).show());
    }

    private boolean validate(String u, String e, String p, String c,
                             String ph, String phar, String loc) {

        boolean ok = true;

        if (u.isEmpty()) { etUser.setError("Required"); ok = false; }

        if (e.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(e).matches()) {
            etEmail.setError("Invalid"); ok = false;
        }

        if (p.length() < 6) { etPass.setError("Min 6 chars"); ok = false; }

        if (!p.equals(c)) { etConfirm.setError("Not match"); ok = false; }

        if (ph.length() < 6) { etPhone.setError("Invalid"); ok = false; }

        if (phar.isEmpty()) { etPharmacy.setError("Required"); ok = false; }

        if (loc.isEmpty()) { etLocation.setError("Required"); ok = false; }

        return ok;
    }
}
