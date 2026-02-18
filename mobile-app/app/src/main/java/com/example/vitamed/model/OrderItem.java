package com.example.vitamed.model;

public class OrderItem {
    public String id;
    public String order_id;
    public String product_id;
    public String name;
    public String brand;      // ✅
    public String dosage;
    public long unit_price;
    public int qty;            // quantity bought
    public long line_total;
    public int returnQty = 0;  // quantity to return (user chooses)

    public OrderItem() {}
}
