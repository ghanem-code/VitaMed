package com.example.vitamed.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.vitamed.R;
import com.example.vitamed.ui.admin.AdminActivity;
import com.example.vitamed.ui.customer.CustomerActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etPassword;
    private Button btnLogin;
    private TextView tvRegister, tvForgot;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin   = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRequestAccount);
        tvForgot   = findViewById(R.id.tvForgotPassword);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        btnLogin.setOnClickListener(v -> doLogin());

        tvRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class))
        );

        tvForgot.setOnClickListener(v -> showForgotPasswordDialog());
    }

    private void doLogin() {
        String username = valueOf(etUsername);
        String pass     = valueOf(etPassword);

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(pass)) {
            toast("Enter username & password");
            return;
        }

        /* ================= 1) FIND USER IN FIRESTORE (username) ================= */
        db.collection("users")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {

                    if (snap.isEmpty()) {
                        toast("Username not found");
                        return;
                    }

                    DocumentSnapshot doc = snap.getDocuments().get(0);

                    String uid = doc.getId();
                    String email = doc.getString("email");

                    // ✅ role (admin / customer)
                    String role = doc.getString("role");
                    if (role == null) role = "customer";
                    role = role.toLowerCase().trim();

                    // ✅ FIX: final role for lambdas
                    final String userRole = role;

                    // ✅ status (Boolean OR String)
                    Object statusObj = doc.get("status");
                    boolean isActivated = false;

                    if (statusObj instanceof Boolean) {
                        isActivated = (Boolean) statusObj;
                    } else if (statusObj instanceof String) {
                        isActivated = ((String) statusObj).equalsIgnoreCase("true");
                    }

                    if (email == null || email.trim().isEmpty()) {
                        toast("No email found for this user");
                        return;
                    }
                    email = email.trim();

                    /* ================= 2) CHECK ACTIVATION ================= */
                    if (!isActivated) {
                        toast("⏳ Please wait for admin to activate your account.");
                        return;
                    }

                    /* ================= 3) AUTH LOGIN (email + password) ================= */
                    auth.signInWithEmailAndPassword(email, pass)
                            .addOnSuccessListener(r -> {

                                FirebaseUser user = auth.getCurrentUser();
                                if (user == null) return;

                                /* ================= 4) EMAIL VERIFICATION (same as web) ================= */
                                // ✅ Customer لازم verify، Admin bypass
                                if (!user.isEmailVerified() && !userRole.equals("admin")) {

                                    user.sendEmailVerification()
                                            .addOnSuccessListener(x -> {
                                                toast("📩 Verification email sent!\n⚠️ Please verify your email then login again.");
                                                auth.signOut();
                                            })
                                            .addOnFailureListener(e -> {
                                                toast("Failed to send verification: " + e.getMessage());
                                                auth.signOut();
                                            });

                                    return;
                                }

                                /* ================= 5) OPTIONAL FIRESTORE UPDATES ================= */
                                db.collection("users").document(uid)
                                        .update(
                                                "lastLoginAt", FieldValue.serverTimestamp(),
                                                "emailVerified", true
                                        );

                                toast("Login Successful 🎉");

                                /* ================= 6) REDIRECT BASED ON ROLE ================= */
                                if (userRole.equals("admin")) {
                                    startActivity(new Intent(this, AdminActivity.class));
                                } else {
                                    startActivity(new Intent(this, CustomerActivity.class));
                                }

                                finish();
                            })
                            .addOnFailureListener(e ->
                                    toast("Login failed: " + e.getMessage()));
                })
                .addOnFailureListener(e ->
                        toast("Error: " + e.getMessage()));
    }

    /* =========================================================
       ✅ FORGOT PASSWORD
       ========================================================= */
    private void showForgotPasswordDialog() {

        String typed = valueOf(etUsername);

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null, false);
        EditText etInput = view.findViewById(R.id.etForgotInput);

        if (!typed.isEmpty()) etInput.setText(typed);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Reset Password")
                .setMessage("Enter your email or username")
                .setView(view)
                .setPositiveButton("Send", null)
                .setNegativeButton("Cancel", (d, w) -> d.dismiss())
                .create();

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String emailOrUsername = etInput.getText().toString().trim();

            if (emailOrUsername.isEmpty()) {
                toast("Enter email or username");
                return;
            }

            // ✅ if input is email => send directly
            if (emailOrUsername.contains("@")) {
                FirebaseAuth.getInstance()
                        .sendPasswordResetEmail(emailOrUsername)
                        .addOnSuccessListener(x -> {
                            toast("✅ Reset link sent");
                            dialog.dismiss();
                        })
                        .addOnFailureListener(e ->
                                toast("Failed: " + e.getMessage()));
                return;
            }

            // ✅ else treat as username => find email then send reset
            db.collection("users")
                    .whereEqualTo("username", emailOrUsername)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(snap -> {

                        if (snap.isEmpty()) {
                            toast("Username not found");
                            return;
                        }

                        String email = snap.getDocuments().get(0).getString("email");
                        if (email == null || email.isEmpty()) {
                            toast("No email registered");
                            return;
                        }

                        FirebaseAuth.getInstance()
                                .sendPasswordResetEmail(email)
                                .addOnSuccessListener(x -> {
                                    toast("✅ Reset link sent to " + email);
                                    dialog.dismiss();
                                })
                                .addOnFailureListener(e ->
                                        toast("Failed: " + e.getMessage()));
                    })
                    .addOnFailureListener(e ->
                            toast("Error: " + e.getMessage()));
        });
    }

    private String valueOf(EditText et) {
        return et != null ? et.getText().toString().trim() : "";
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
