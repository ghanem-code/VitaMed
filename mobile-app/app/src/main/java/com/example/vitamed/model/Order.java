package com.example.vitamed.model;

import com.google.firebase.Timestamp;

public class Order {
    public String id;          // Firestore doc id
    public long number;        // 👈 رقم الفاتورة التسلسلي
    public String user_id;
    public long total;
    public String brand;      // ✅
    public String dosage;
    public String status;
    public Timestamp created_at;

    public Order() {}
}
