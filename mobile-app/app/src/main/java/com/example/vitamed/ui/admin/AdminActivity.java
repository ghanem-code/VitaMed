package com.example.vitamed.ui.admin;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.vitamed.R;
import com.example.vitamed.ui.auth.LoginActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;

public class AdminActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // Cards
        MaterialCardView cardProducts = findViewById(R.id.cardProducts);
        MaterialCardView cardUsers    = findViewById(R.id.cardUsers);
        MaterialCardView cardFeedback = findViewById(R.id.cardFeedback);
        MaterialCardView cardInvoices = findViewById(R.id.cardInvoices);
        MaterialCardView cardLogout   = findViewById(R.id.cardLogout);

        // Navigate to screens
        cardProducts.setOnClickListener(v ->
                startActivity(new Intent(this, AdminProductsActivity.class)));

        cardUsers.setOnClickListener(v ->
                startActivity(new Intent(this, AdminManageUsersActivity.class)));

        cardFeedback.setOnClickListener(v ->
                startActivity(new Intent(this, AdminFeedbackActivity.class)));

        cardInvoices.setOnClickListener(v ->
                startActivity(new Intent(this, AdminInvoicesActivity.class)));

        // Logout (correct)
        cardLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });
    }
}
