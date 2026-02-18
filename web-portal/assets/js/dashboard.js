import { auth, db } from "./firebase.js";
import { signOut } from "https://www.gstatic.com/firebasejs/11.0.1/firebase-auth.js";

import {
  collection,
  query,
  where,
  orderBy,
  limit,
  onSnapshot
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

/* ================= ANIMATION ================= */

function animateCount(element, targetValue, duration = 800) {
  let startTime = null;

  function update(timestamp) {
    if (!startTime) startTime = timestamp;

    const progress = Math.min((timestamp - startTime) / duration, 1);
    const current = targetValue * progress;

    if (element.id === "monthlyVolume") {
      if (progress < 1) {
        element.textContent = `$${current.toFixed(2)}`;
      } else {
        let formatted =
          targetValue >= 1000
            ? `$${(targetValue / 1000).toFixed(1)}k`
            : `$${targetValue.toFixed(2)}`;
        element.textContent = formatted;
      }
    } else {
      element.textContent = Math.floor(current);
    }

    if (progress < 1) requestAnimationFrame(update);
  }

  requestAnimationFrame(update);
}

/* ================= MAIN ================= */

let unsubInvoices = null;
let unsubItemsMap = new Map(); // order_id -> unsub()

function clearItemsListeners() {
  unsubItemsMap.forEach((u) => u && u());
  unsubItemsMap.clear();
}

auth.onAuthStateChanged((user) => {
  if (!user) {
    window.location.href = "../login.html";
    return;
  }

  const uid = user.uid;

  const name = localStorage.getItem("username") || "Customer";
  document.getElementById("username").textContent = name;
  document.getElementById("profileName").textContent = name;
  document.getElementById("avatarInitial").textContent =
    name.trim().charAt(0).toUpperCase();

  /* ================= DASHBOARD NUMBERS (REAL-TIME) ================= */

  if (unsubInvoices) unsubInvoices();
  clearItemsListeners();

  const invQuery = query(
    collection(db, "invoices"),
    where("user_id", "==", uid)
  );

  // ✅ caches
  let invoicesCache = [];
  let itemsCountByOrder = new Map();

  function recalcAndRender() {
    let totalInvoices = 0;
    let activeOrders = 0;
    let completedOrders = 0;
    let pendingReturns = 0;
    let totalLBP = 0;

    for (const inv of invoicesCache) {
      const orderId = inv.order_id;
      const itemsCount = itemsCountByOrder.get(orderId) || 0;

      // ✅ تجاهل الفواتير الفارغة
      if (itemsCount === 0 && !inv.updated_total) continue;

      totalInvoices++;

      const st = (inv.status || "").toLowerCase();

      // ✅ Active Orders = pending + processing
      if (st === "pending" || st === "processing") activeOrders++;

      // ✅ Completed Orders
      if (st === "completed") completedOrders++;

      // ✅ Returns
      if (st === "partially_returned" || st === "returned") pendingReturns++;

      // ✅ Monthly Volume (LBP)
      if (inv.updated_total) totalLBP += inv.updated_total;
    }

    const usd = totalLBP / 89500;

    // ✅ existing in your dashboard
    const elActive = document.getElementById("activeOrders");
    const elReturns = document.getElementById("pendingReturns");
    const elVolume = document.getElementById("monthlyVolume");

    if (elActive) animateCount(elActive, activeOrders);
    if (elReturns) animateCount(elReturns, pendingReturns);
    if (elVolume) animateCount(elVolume, usd);

    // ✅ optional: if you add them later in HTML
    const elTotalInvoices = document.getElementById("totalInvoices");
    const elCompleted = document.getElementById("completedOrders");

    if (elTotalInvoices) animateCount(elTotalInvoices, totalInvoices);
    if (elCompleted) animateCount(elCompleted, completedOrders);
  }

  unsubInvoices = onSnapshot(invQuery, (snap) => {
    invoicesCache = snap.docs.map((d) => ({
      id: d.id,
      ...d.data(),
    }));

    // ✅ افتح listeners لـ order_items لكل order_id
    invoicesCache.forEach((inv) => {
      const orderId = inv.order_id;
      if (!orderId) return;

      if (unsubItemsMap.has(orderId)) return;

      const itemsQ = query(
        collection(db, "order_items"),
        where("order_id", "==", orderId)
      );

      const unsubItems = onSnapshot(itemsQ, (itemsSnap) => {
        itemsCountByOrder.set(orderId, itemsSnap.size);
        recalcAndRender();
      });

      unsubItemsMap.set(orderId, unsubItems);
    });

    // ✅ شيل listeners للطلبات يلي انحذفت
    const currentOrders = new Set(
      invoicesCache.map((i) => i.order_id).filter(Boolean)
    );

    Array.from(unsubItemsMap.keys()).forEach((orderId) => {
      if (!currentOrders.has(orderId)) {
        unsubItemsMap.get(orderId)();
        unsubItemsMap.delete(orderId);
        itemsCountByOrder.delete(orderId);
      }
    });

    recalcAndRender();
  });

  /* ================= NOTIFICATIONS (REAL-TIME) ================= */

  const notifList = document.getElementById("notifList");
  const notifCount = document.getElementById("notifCount");
  const notifBadge = document.getElementById("notifBadge");

  const activityQuery = query(
    collection(db, "admin_activity"),
    orderBy("timestamp", "desc"),
    limit(6)
  );

  onSnapshot(activityQuery, (activitySnap) => {
    notifList.innerHTML = "";

    notifCount.textContent = activitySnap.size;
    notifBadge.textContent = activitySnap.size;

    if (activitySnap.empty) {
      notifList.innerHTML =
        "<div class='notif-item'>No notifications yet</div>";
      return;
    }

    activitySnap.forEach((doc) => {
      const n = doc.data();
      const div = document.createElement("div");

      const type = n.type || "add";
      div.className = "notif-item " + type;

      const time = n.timestamp?.toDate()?.toLocaleString() || "Just now";

      div.innerHTML = `
        <div><b>${n.title || "System"}</b></div>
        <div>${n.message || ""}</div>
        <div class="notif-time">${time}</div>
      `;

      notifList.appendChild(div);
    });
  });
});

/* ================= LOGOUT ✅ (FIXED) ================= */
const logoutBtn = document.getElementById("logoutSidebar");
if (logoutBtn) {
  logoutBtn.onclick = async () => {
    try {
      localStorage.clear();
      await signOut(auth);
      window.location.href = "../login.html";
    } catch (e) {
      console.log("Logout error:", e);
      window.location.href = "../login.html";
    }
  };
}

/* ================= NOTIFICATION DROPDOWN ================= */
const notifBtn = document.getElementById("notifBtn");
const notifDropdown = document.getElementById("notifDropdown");

if (notifBtn && notifDropdown) {
  notifBtn.onclick = (e) => {
    e.stopPropagation();
    notifDropdown.style.display =
      notifDropdown.style.display === "flex" ? "none" : "flex";
  };

  document.addEventListener("click", () => {
    notifDropdown.style.display = "none";
  });
}
