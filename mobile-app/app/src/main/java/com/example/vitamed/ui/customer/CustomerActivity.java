package com.example.vitamed.ui.customer;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.vitamed.R;
import com.example.vitamed.adapter.NotificationsAdapter;
import com.example.vitamed.model.NotificationItem;
import com.example.vitamed.ui.auth.LoginActivity;
import com.example.vitamed.ui.order.CustomerChatActivity;
import com.example.vitamed.ui.order.OrderActivity;
import com.example.vitamed.ui.order.ReturnActivity;
import com.example.vitamed.ui.order.recordActivity;
import com.example.vitamed.ui.price.PriceActivity;
import com.example.vitamed.ui.settings.SettingsActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CustomerActivity extends AppCompatActivity {

    private ImageView ivBanner, ivBell, ivMenu;
    private LinearLayout panel;
    private TextView tvClose;
    private RecyclerView rvNotif;
    private NotificationsAdapter notifAdapter;

    private TextView tvWelcome;
    private List<View> dots;
    private int current = 0;

    private final int[] banners = new int[]{
            R.drawable.dot5,
            R.drawable.dot1,
            R.drawable.dot2,
            R.drawable.dot3,
            R.drawable.dot6
    };

    // ✅ Real-time notification listener
    private ListenerRegistration notifListener;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_customer);

        tvWelcome = findViewById(R.id.tvWelcome);

        ivBanner = findViewById(R.id.ivBanner);
        View dot1 = findViewById(R.id.dot1);
        View dot2 = findViewById(R.id.dot2);
        View dot3 = findViewById(R.id.dot3);
        View dot4 = findViewById(R.id.dot4);
        View dot5 = findViewById(R.id.dot5);
        dots = Arrays.asList(dot1, dot2, dot3, dot4, dot5);
        updateBanner(0);

        ivMenu = findViewById(R.id.ivMenu);
        ivMenu.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class))
        );

        panel = findViewById(R.id.panelNotifications);
        tvClose = findViewById(R.id.tvClosePanel);
        rvNotif = findViewById(R.id.rvNotifInline);

        rvNotif.setLayoutManager(new LinearLayoutManager(this));
        notifAdapter = new NotificationsAdapter();
        rvNotif.setAdapter(notifAdapter);

        ivBell = findViewById(R.id.ivBell);
        ivBell.setOnClickListener(v -> {
            // ✅ Open panel
            panel.animate().translationY(0).setDuration(350).start();

            // ✅ Start REALTIME notifications (آخر 6 + الجديد فوق)
            startNotificationsRealtime_Last6_NewestFirst();
        });

        tvClose.setOnClickListener(v -> {
            // ✅ Close panel
            panel.animate().translationY(-1000).setDuration(350).start();

            // ✅ Stop listener
            stopNotificationsRealtime();
        });

        // ===================== READ USERNAME LIKE WEBSITE ======================
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(auth.getCurrentUser().getUid())
                    .get()
                    .addOnSuccessListener(doc -> {

                        String username = doc.getString("username");

                        if (username == null || username.isEmpty())
                            username = "Customer";

                        username = username.replace("Dr.", "")
                                .replace("Dr", "")
                                .trim();

                        tvWelcome.setText("Welcome, " + username);
                    });
        }
        // =======================================================================

        for (int i = 0; i < dots.size(); i++) {
            final int idx = i;
            dots.get(i).setOnClickListener(v -> updateBanner(idx));
        }

        findViewById(R.id.cardPlaceOrder).setOnClickListener(v ->
                startActivity(new Intent(this, OrderActivity.class))
        );

        findViewById(R.id.cardTrackOrder).setOnClickListener(v ->
                startActivity(new Intent(this, CustomerChatActivity.class))
        );

        findViewById(R.id.cardPriceList).setOnClickListener(v ->
                startActivity(new Intent(this, PriceActivity.class))
        );

        findViewById(R.id.cardRequestReturn).setOnClickListener(v ->
                startActivity(new Intent(this, ReturnActivity.class))
        );

        findViewById(R.id.cardMyRecords).setOnClickListener(v ->
                startActivity(new Intent(this, recordActivity.class))
        );

        findViewById(R.id.cardWishToPay).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });

        updateUnreadBadge();
    }

    // ✅ REALTIME + last 6 + NEWEST FIRST (جديد -> قديم)
    private void startNotificationsRealtime_Last6_NewestFirst() {

        // ✅ prevent multiple listeners
        stopNotificationsRealtime();

        notifListener = FirebaseFirestore.getInstance()
                .collection("admin_activity")
                .orderBy("timestamp", Query.Direction.DESCENDING) // ✅ الجديد بالأول
                .limit(6)
                .addSnapshotListener((snap, e) -> {

                    if (e != null) return;
                    if (snap == null) return;

                    List<NotificationItem> list = new ArrayList<>();
                    snap.getDocuments().forEach(d -> {
                        NotificationItem n = d.toObject(NotificationItem.class);
                        if (n != null) list.add(n);
                    });

                    // ✅ IMPORTANT: NO reverse ✅
                    notifAdapter.replaceAll(list);
                });
    }

    private void stopNotificationsRealtime() {
        if (notifListener != null) {
            notifListener.remove();
            notifListener = null;
        }
    }

    private void updateBanner(int index) {
        if (index < 0) index = 0;
        if (index >= banners.length) index = banners.length - 1;
        current = index;

        ivBanner.setImageResource(banners[index]);

        for (int i = 0; i < dots.size(); i++) {
            dots.get(i).setSelected(i == index);
        }
    }

    private void updateUnreadBadge() {

        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;

        String uid = auth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("chat_rooms")
                .whereEqualTo("admin_id", "admin")
                .whereEqualTo("user_id", uid)
                .limit(1)
                .get()
                .addOnSuccessListener(roomSnap -> {

                    if (roomSnap.isEmpty()) return;

                    String roomId = roomSnap.getDocuments().get(0).getId();

                    db.collection("chat_messages")
                            .whereEqualTo("room_id", roomId)
                            .whereEqualTo("sender_role", "admin")
                            .whereEqualTo("seen", false)
                            .addSnapshotListener((msgSnap, e) -> {

                                TextView badge = findViewById(R.id.tvBadge);
                                int count = (msgSnap == null) ? 0 : msgSnap.size();

                                if (count > 0) {
                                    badge.setVisibility(View.VISIBLE);
                                    badge.setText(String.valueOf(count));
                                } else {
                                    badge.setVisibility(View.GONE);
                                }
                            });
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopNotificationsRealtime();
    }
}
