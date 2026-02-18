package com.example.vitamed.ui.feedback;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vitamed.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class FeedbackActivity extends AppCompatActivity {

    private EditText etMessage;
    private RatingBar ratingBar;
    private Button btnSend;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feedback);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etMessage  = findViewById(R.id.etFeedbackMessage);
        ratingBar  = findViewById(R.id.ratingBarFeedback);
        btnSend    = findViewById(R.id.btnSendFeedback);

        btnSend.setOnClickListener(v -> sendFeedback());
    }

    private void sendFeedback() {
        String msg = etMessage.getText().toString().trim();
        float rating = ratingBar.getRating();

        if (TextUtils.isEmpty(msg)) {
            Toast.makeText(this, "Feedback cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        if (rating <= 0f) {
            Toast.makeText(this, "Please select a rating", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();

        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Sending feedback...");
        pd.setCancelable(false);
        pd.show();

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {

                    String pharmacyName =
                            doc.getString("pharmacyname") != null && !doc.getString("pharmacyname").isEmpty()
                                    ? doc.getString("pharmacyname")
                                    : (doc.getString("fullName") != null && !doc.getString("fullName").isEmpty()
                                    ? doc.getString("fullName")
                                    : (doc.getString("username") != null ? doc.getString("username") : "Unknown"));

                    // prepare doc
                    com.google.firebase.firestore.DocumentReference docRef =
                            db.collection("feedback").document();

                    Map<String, Object> data = new HashMap<>();
                    data.put("id", docRef.getId());
                    data.put("user_id", uid);
                    data.put("pharmacyname", pharmacyName);
                    data.put("message", msg);
                    data.put("rating", rating);
                    data.put("image_url", "");  // always empty
                    data.put("created_at", FieldValue.serverTimestamp());

                    docRef.set(data)
                            .addOnSuccessListener(r -> {
                                pd.dismiss();
                                Toast.makeText(this, "Thank you for your feedback!", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .addOnFailureListener(e -> {
                                pd.dismiss();
                                Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });

                })
                .addOnFailureListener(e -> {
                    pd.dismiss();
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}
