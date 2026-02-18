package com.example.vitamed.model;

import com.google.firebase.Timestamp;

public class NotificationItem {
    public long id;
    public String type;
    public String message;
    public String productId;
    public Timestamp timestamp;

    public NotificationItem() {}  // Firestore needs empty constructor
}
