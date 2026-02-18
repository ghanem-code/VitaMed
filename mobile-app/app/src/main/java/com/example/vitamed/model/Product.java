// Product.java
package com.example.vitamed.model;

import com.google.firebase.Timestamp;

public class Product {
    public String docId;       // ← ID الوثيقة من Firestore
    public long id;            // رقم إضافي إذا بدك رقم داخلي
    public String name;
    public String brand;

    public String dosage;
    public long availability;
    public long price;

    public Timestamp created_at;
    public int qty;

    public Product() {}

    public boolean isAvailable() {
        return availability > 0;
    }
}
