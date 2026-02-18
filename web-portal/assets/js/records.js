import { auth, db } from "./firebase.js";
import { setupLogout } from "./logout.js";
import {
  collection,
  query,
  where,
  onSnapshot
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";
import {
  onAuthStateChanged
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-auth.js";

const ordersList = document.getElementById("ordersList");
const loadingText = document.getElementById("recordsLoading");

function formatDate(ts) {
  if (!ts) return "-";
  if (ts.toDate) return ts.toDate().toLocaleString();
  if (ts.seconds) return new Date(ts.seconds * 1000).toLocaleString();
  return String(ts);
}

function formatMoney(v) {
  if (typeof v !== "number") return v || "0";
  return v.toLocaleString("en-LB");
}

// ✅ listeners holders
let unsubInvoices = null;
let unsubItemsMap = new Map(); // key = invoiceId => unsub()

function clearItemListeners() {
  unsubItemsMap.forEach((u) => u && u());
  unsubItemsMap.clear();
}

function renderRecords(invoicesCache) {
  ordersList.innerHTML = "";

  if (!invoicesCache || invoicesCache.length === 0) {
    loadingText.style.display = "block";
    loadingText.textContent = "No records found for this account.";
    return;
  }

  // ✅ فلترة: invoices يلي updated_total > 0 وعندها items
  const validInvoices = invoicesCache.filter(inv => {
    const totalOk = inv.updated_total && inv.updated_total > 0;
    const itemsOk = (inv.items || []).length > 0;
    return totalOk && itemsOk;
  });

  if (validInvoices.length === 0) {
    loadingText.style.display = "block";
    loadingText.textContent = "No records found for this account.";
    return;
  }

  loadingText.style.display = "none";

  // ✅ ترتيب الأحدث أولاً
  validInvoices.sort((a, b) => {
    const ta = a.created_at?.seconds ? a.created_at.seconds * 1000 : (a.created_at?.toDate?.()?.getTime() || 0);
    const tb = b.created_at?.seconds ? b.created_at.seconds * 1000 : (b.created_at?.toDate?.()?.getTime() || 0);
    return tb - ta;
  });

  for (const inv of validInvoices) {
    const status = inv.status || "completed";

    const card = document.createElement("div");
    card.className = "record-item";

    card.innerHTML = `
      <div class="record-header">
        <div>
          <div class="r-id">Invoice #${inv.number ?? "—"}</div>
          <div class="r-date">${formatDate(inv.created_at)}</div>
        </div>

        <div class="r-right">
          <div class="r-total">${formatMoney(inv.updated_total)} LBP</div>
          <div class="r-status r-status-${status}">${status}</div>
        </div>
      </div>

      <div class="record-lines">
        ${
          (inv.items || []).length === 0
            ? "<p class='no-lines'>No items.</p>"
            : (inv.items || []).map(it => `
              <div class="record-line">
                <div class="rl-name">${it.name}</div>
                <div class="rl-qty">x${it.qty}</div>
                <div class="rl-price">${formatMoney(it.line_total)} LBP</div>
              </div>
            `).join("")
        }
      </div>
    `;

    card.onclick = () => {
      window.location.href = `record_details.html?id=${inv.id}`;
    };

    ordersList.appendChild(card);
  }
}

function listenRecords(uid) {
  loadingText.textContent = "Loading orders...";
  loadingText.style.display = "block";
  ordersList.innerHTML = "";

  if (unsubInvoices) unsubInvoices();
  clearItemListeners();

  const invQuery = query(
    collection(db, "invoices"),
    where("user_id", "==", uid)
  );

  let invoicesCache = [];

  unsubInvoices = onSnapshot(invQuery, (invSnap) => {
    invoicesCache = invSnap.docs.map(d => ({
      id: d.id,
      ...d.data(),
      items: []
    }));

    if (invoicesCache.length === 0) {
      renderRecords([]);
      return;
    }

    // ✅ لكل invoice: نسمع الـ order_items real time
    invoicesCache.forEach(inv => {
      // close old listener for same invoice
      if (unsubItemsMap.has(inv.id)) {
        unsubItemsMap.get(inv.id)();
        unsubItemsMap.delete(inv.id);
      }

      const itemsQ = query(
        collection(db, "order_items"),
        where("order_id", "==", inv.order_id)
      );

      const unsubItems = onSnapshot(itemsQ, (itemsSnap) => {
        const items = itemsSnap.docs.map(x => ({ id: x.id, ...x.data() }));

        const index = invoicesCache.findIndex(a => a.id === inv.id);
        if (index !== -1) {
          invoicesCache[index].items = items;
        }

        renderRecords(invoicesCache);
      });

      unsubItemsMap.set(inv.id, unsubItems);
    });

    // أول render
    renderRecords(invoicesCache);
  });
}

onAuthStateChanged(auth, user => {
  if (!user) {
    if (unsubInvoices) unsubInvoices();
    clearItemListeners();

    loadingText.style.display = "block";
    loadingText.textContent = "Please login again.";
    ordersList.innerHTML = "";
    return;
  }

  listenRecords(user.uid);
});
setupLogout();