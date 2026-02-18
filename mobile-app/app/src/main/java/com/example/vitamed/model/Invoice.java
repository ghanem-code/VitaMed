package com.example.vitamed.model;

import com.google.firebase.Timestamp;
import java.util.List;

public class Invoice {

    public String id;

    public String user_id;
    public String order_id;

    public long number;
    public long original_total;
    public long updated_total;
    public long return_total;

    public String status;

    public Timestamp created_at;
    public Timestamp updated_at;
    public Timestamp returned_at;

    // اسم الصيدلية من users
    public String pharmacyname;


    // عناصر الفاتورة من order_items
    public List<InvoiceItem> items;

    public Invoice() {}

    public long getFinalTotal() {
        if (updated_total != 0) return updated_total;
        if (original_total != 0) return original_total;
        return return_total;
    }
}
