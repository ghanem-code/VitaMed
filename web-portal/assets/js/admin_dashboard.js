import { db, auth } from "./firebase.js";
import { setupLogout } from "./logout.js";

import {
  collection,
  query,
  where,
  onSnapshot
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

const RATE = 89500;

/* ================= CACHES ================= */
let usersCache = [];
let invoicesCache = [];
let productsCache = [];
let itemsByOrder = new Map(); // order_id => items[]

/* ================= LISTENERS ================= */
let unsubUsers = null;
let unsubInvoices = null;
let unsubProducts = null;
let unsubItemsMap = new Map(); // order_id => unsub()

/* ================= INIT ================= */
auth.onAuthStateChanged(user => {
  if (!user) {
    window.location.href = "../login.html";
    return;
  }
  startDashboardRealTime();
});

/* ================= REAL-TIME START ================= */
function startDashboardRealTime() {

  // stop old
  if (unsubUsers) unsubUsers();
  if (unsubInvoices) unsubInvoices();
  if (unsubProducts) unsubProducts();
  clearItemsListeners();

  // ✅ USERS Real-Time
  unsubUsers = onSnapshot(collection(db, "users"), (snap) => {
    usersCache = snap.docs.map(d => ({ id: d.id, ...d.data() }));
    recalcDashboard();
  });

  // ✅ INVOICES Real-Time
  unsubInvoices = onSnapshot(collection(db, "invoices"), (snap) => {
    invoicesCache = snap.docs.map(d => ({ id: d.id, ...d.data() }));
    recalcDashboard();
    listenTopMedsRealTime(); // ✅ Top 5 real-time
  });

  // ✅ PRODUCTS Real-Time
  unsubProducts = onSnapshot(collection(db, "products"), (snap) => {
    productsCache = snap.docs.map(d => ({ id: d.id, ...d.data() }));
    recalcDashboard();
  });
}

/* ================= DASHBOARD CALC ================= */
function recalcDashboard() {

  /* ✅ USERS (exclude admin from pharmacy count) ✅ */
  const pharmaciesOnly = usersCache.filter(u =>
    (u.username || "").toLowerCase() !== "admin"
  );

  /* ===== USERS ===== */
  setText("pharmacyCount", pharmaciesOnly.length);

  let active = 0, blocked = 0;

  pharmaciesOnly.forEach(u => {
    // ✅ FIX: blocked based on status
    const isBlocked = (u.status === false);
    if (isBlocked) blocked++;
    else active++;
  });

  setText("activeBlocked", `${active} Active / ${blocked} Blocked`);

  /* ===== INVOICES ===== */
  setText("invoiceCount", invoicesCache.length);

  const returnsCount = invoicesCache.filter(inv =>
    inv.status === "returned" || inv.status === "partially_returned"
  ).length;

  setText("returnCount", returnsCount);

  /* ===== SALES (completed فقط) ===== */
  let totalLBP = 0;

  invoicesCache.forEach(inv => {
    if (inv.status !== "completed") return;
    const v = Number(inv.updated_total || inv.original_total || 0);
    if (v > 0) totalLBP += v;
  });

  setText("totalSalesLBP", totalLBP.toLocaleString("en-LB") + " LBP");
  setText("totalSalesUSD", "$" + (totalLBP / RATE).toFixed(2));

  /* ===== LOW STOCK ===== */
  let low = 0;
  productsCache.forEach(p => {
    if (Number(p.availability || 0) <= 10) low++;
  });
  setText("lowStockCount", low);

  /* ===== SAFETY PANEL ===== */
  setText("sLowStock", low);
  setText("sReturns", returnsCount);
  setText("sBlocked", blocked);
  setText("sStatus", "OK");

  const riskBadge = document.getElementById("riskBadge");
  if (riskBadge) {
    const risk =
      (low >= 10 ? 2 : low >= 5 ? 1 : 0) +
      (returnsCount >= 5 ? 2 : returnsCount >= 2 ? 1 : 0);

    if (risk >= 3) {
      riskBadge.textContent = "HIGH RISK";
      riskBadge.className = "badge badge-danger";
    } else if (risk >= 1) {
      riskBadge.textContent = "MEDIUM RISK";
      riskBadge.className = "badge badge-warn";
    } else {
      riskBadge.textContent = "LOW RISK";
      riskBadge.className = "badge badge-ok";
    }
  }

  /* ===== COMPLIANCE ===== */
  setText("cActive", active);
  setText("cBlocked", blocked);
  setText("cPending", 0);

  const score = Math.round((active / Math.max(pharmaciesOnly.length, 1)) * 100);
  setText("cScore", score + "%");
  const bar = document.getElementById("cBar");
  if (bar) bar.style.width = score + "%";

  /* ===== SMART INSIGHTS ===== */
  const insights = [];

  if (low >= 10) insights.push(`⚠️ Critical low stock: ${low} items.`);
  else if (low >= 5) insights.push(`🟡 Low stock warning: ${low} items.`);
  else insights.push(`✅ Inventory stable.`);

  if (returnsCount > 0) insights.push(`🧾 ${returnsCount} returned invoices.`);
  else insights.push(`✅ No return issues detected.`);

  if (blocked > 0) insights.push(`🛑 ${blocked} pharmacies blocked.`);
  else insights.push(`✅ All pharmacies compliant.`);

  if (totalLBP > 0) insights.push(`📈 Sales: ${totalLBP.toLocaleString("en-LB")} LBP.`);
  else insights.push(`ℹ️ No completed sales yet.`);

  const list = document.getElementById("insightsList");
  if (list) list.innerHTML = insights.map(t => `<li>${t}</li>`).join("");

  // ✅ animation optional
  animateStats(["lowStockCount", "pharmacyCount", "invoiceCount", "returnCount"]);
}

/* ================= TOP 5 MEDS REAL-TIME ================= */
function clearItemsListeners() {
  unsubItemsMap.forEach(u => u && u());
  unsubItemsMap.clear();
  itemsByOrder.clear();
}

function listenTopMedsRealTime() {

  const topBox = document.getElementById("topMeds");
  if (!topBox) return;

  const completed = invoicesCache.filter(i => i.status === "completed");
  const orderIds = completed.map(i => i.order_id).filter(Boolean);

  if (orderIds.length === 0) {
    topBox.innerHTML = `<div class="top-empty">No data yet</div>`;
    clearItemsListeners();
    return;
  }

  const activeSet = new Set(orderIds);

  // close removed
  Array.from(unsubItemsMap.keys()).forEach(orderId => {
    if (!activeSet.has(orderId)) {
      unsubItemsMap.get(orderId)();
      unsubItemsMap.delete(orderId);
      itemsByOrder.delete(orderId);
    }
  });

  // open missing
  orderIds.forEach(orderId => {

    if (unsubItemsMap.has(orderId)) return;

    const itemsQ = query(
      collection(db, "order_items"),
      where("order_id", "==", orderId)
    );

    const unsub = onSnapshot(itemsQ, (snap) => {
      const items = snap.docs.map(d => d.data());
      itemsByOrder.set(orderId, items);
      renderTopMeds();
    });

    unsubItemsMap.set(orderId, unsub);
  });

  renderTopMeds();
}

function renderTopMeds() {

  const topBox = document.getElementById("topMeds");
  if (!topBox) return;

  const agg = new Map();

  itemsByOrder.forEach(items => {
    items.forEach(it => {
      const qty = Number(it.qty || 0);
      if (qty <= 0) return;

      const name = (it.name || "Unknown").trim();
      const key = String(it.product_id || name || "Unknown");

      if (!agg.has(key)) {
        agg.set(key, { name, totalQty: 0, occurrences: 0 });
      }

      const row = agg.get(key);
      row.totalQty += qty;
      row.occurrences += 1;
    });
  });

  const arr = Array.from(agg.values());
  if (arr.length === 0) {
    topBox.innerHTML = `<div class="top-empty">No data yet</div>`;
    return;
  }

  arr.sort((a, b) => b.totalQty - a.totalQty);
  const top5 = arr.slice(0, 5);

  topBox.innerHTML = top5.map((m, idx) => `
    <div class="top-item">
      <div>
        <b>${idx + 1}. ${escapeHtml(m.name)}</b><br>
        <small>Used in ${m.occurrences} items • Total Qty: ${Number(m.totalQty).toLocaleString("en-LB")}</small>
      </div>
      <div><b>${Number(m.totalQty).toLocaleString("en-LB")}</b></div>
    </div>
  `).join("");
}

/* ================= HELPERS ================= */
function escapeHtml(str) {
  return String(str || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

function animateStats(ids) {
  ids.forEach(id => {
    const el = document.getElementById(id);
    if (!el) return;

    const target = Number((el.textContent || "").replace(/\D/g, "")) || 0;
    let cur = 0;
    const step = Math.max(1, Math.ceil(target / 30));

    const t = setInterval(() => {
      cur += step;
      if (cur >= target) {
        el.textContent = target.toLocaleString("en-LB");
        clearInterval(t);
      } else {
        el.textContent = cur.toLocaleString("en-LB");
      }
    }, 16);
  });
}

setupLogout();
