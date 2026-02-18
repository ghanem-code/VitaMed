import { db } from "./firebase.js";
import { setupLogout } from "./logout.js";

import {
  collection,
  query,
  where,
  doc,
  onSnapshot
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

function fmt(n) {
  return Number(n || 0).toLocaleString("en-LB");
}

function fmtDate(ts) {
  if (!ts) return "-";
  if (ts.toDate) return ts.toDate().toLocaleString();
  if (ts.seconds) return new Date(ts.seconds * 1000).toLocaleString();
  return String(ts);
}

let unsubInvoice = null;
let unsubItems = null;

function loadInvoiceRealTime() {
  const params = new URLSearchParams(window.location.search);
  const id = params.get("id");

  if (!id) {
    alert("Invalid invoice");
    return;
  }

  // ✅ Stop old listeners
  if (unsubInvoice) unsubInvoice();
  if (unsubItems) unsubItems();

  const invRef = doc(db, "invoices", id);

  unsubInvoice = onSnapshot(invRef, (invSnap) => {
    if (!invSnap.exists()) {
      alert("Invoice not found");
      return;
    }

    const inv = invSnap.data();

    document.getElementById("invoiceNumber").textContent = "Invoice #" + inv.number;
    document.getElementById("inv-id").textContent = "Invoice #" + inv.number;
    document.getElementById("inv-date").textContent = fmtDate(inv.created_at);
    document.getElementById("inv-total").textContent = fmt(inv.updated_total) + " LBP";

    // ✅ items listener (real-time)
    if (unsubItems) unsubItems();

    const itemsQ = query(
      collection(db, "order_items"),
      where("order_id", "==", inv.order_id)
    );

    unsubItems = onSnapshot(itemsQ, (itemsSnap) => {
      const itemsBox = document.getElementById("itemsContainer");
      itemsBox.innerHTML = "";

      if (itemsSnap.empty) {
        itemsBox.innerHTML = `<p style="color:#64748b;">No items found.</p>`;
        return;
      }

      itemsSnap.forEach(d => {
        const it = d.data();

        itemsBox.innerHTML += `
          <div class="item">
            <div>
              <div class="item-name">${it.name}</div>
              <div class="item-qty">x${it.qty}</div>
            </div>
            <div class="item-price">${fmt(it.line_total)} LBP</div>
          </div>
        `;
      });
    });
  });
}

loadInvoiceRealTime();
setupLogout();
