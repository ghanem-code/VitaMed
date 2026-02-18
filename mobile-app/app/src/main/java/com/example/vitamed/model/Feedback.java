package com.example.vitamed.model;

import com.google.firebase.Timestamp;

public class Feedback {
    public String id;
    public String user_id;
    public String pharmacyname;
    public String message;
    public float rating;         // ⭐ التقييم من 1 لـ 5
    public String image_url;     // رابط الصورة (اختياري)
    public Timestamp created_at;

    public Feedback() {}
}
