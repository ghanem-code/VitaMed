package com.example.vitamed.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vitamed.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class EmailOtpActivity extends AppCompatActivity {

    private EditText etOtp;
    private TextView tvEmailLabel;
    private Button btnConfirm, btnResend;

    private int expectedOtp;
    private String username, email, password, phone, pharmacy;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_email_otp);

        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();

        etOtp        = findViewById(R.id.etOtp);
        tvEmailLabel = findViewById(R.id.tvEmailLabel);
        btnConfirm   = findViewById(R.id.btnConfirmOtp);
        btnResend    = findViewById(R.id.btnResendOtp);

        // نستقبل البيانات من RegisterActivity
        username  = getIntent().getStringExtra("username");
        email     = getIntent().getStringExtra("email");
        password  = getIntent().getStringExtra("password");
        phone     = getIntent().getStringExtra("phone");
        pharmacy  = getIntent().getStringExtra("pharmacy");
        expectedOtp = getIntent().getIntExtra("otp", 0);

        tvEmailLabel.setText("We've sent a code to:\n" + email);

        btnConfirm.setOnClickListener(v -> checkOtpAndRegister());
        btnResend.setOnClickListener(v -> resendOtp());
    }

    private void checkOtpAndRegister() {
        String entered = etOtp.getText().toString().trim();

        if (entered.isEmpty()) {
            etOtp.setError("Enter the code");
            return;
        }

        if (!entered.matches("\\d{4,6}")) {
            etOtp.setError("Invalid code format");
            return;
        }

        int enteredInt = Integer.parseInt(entered);
        if (enteredInt != expectedOtp) {
            etOtp.setError("Wrong code");
            return;
        }

        // ✅ إذا الكود صح → منعمل حساب فعلي
        createAuthAndProfile();
    }

    private void resendOtp() {
        // هوني إذا بدك تعمل re-generate للكود وتبعتو من جديد
        Toast.makeText(this, "Resend not implemented (for demo)", Toast.LENGTH_SHORT).show();
    }

    private void createAuthAndProfile() {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(r -> {
                    String uid = r.getUser().getUid();

                    Map<String, Object> doc = new HashMap<>();
                    doc.put("uid", uid);
                    doc.put("username", username);
                    doc.put("email", email);
                    doc.put("phone", phone);
                    doc.put("pharmacyname", pharmacy);
                    doc.put("createdAt", System.currentTimeMillis());

                    db.collection("users").document(uid).set(doc)
                            .addOnSuccessListener(v -> {
                                Toast.makeText(this,
                                        "Account created successfully",
                                        Toast.LENGTH_LONG).show();

                                // بعد النجاح → روح على LoginActivity
                                Intent i = new Intent(this, LoginActivity.class);
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(i);
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(this,
                                            "Profile save failed: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Register failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }
}
