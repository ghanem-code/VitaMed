import { db } from "./firebase.js";
import { setupLogout } from "./logout.js";
import {
  doc,
  collection,
  where,
  query,
  updateDoc,
  onSnapshot
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

const id = new URLSearchParams(window.location.search).get("id");

const title = document.getElementById("title");
const pharmacy = document.getElementById("pharmacy");
const date = document.getElementById("date");
const status = document.getElementById("status");
const total = document.getElementById("total");
const itemsTable = document.getElementById("itemsTable");

let unsubInvoice = null;
let unsubItems = null;
let unsubUser = null;

function fmtDate(ts) {
  if (!ts) return "-";
  if (ts.toDate) return ts.toDate().toLocaleString();
  if (ts.seconds) return new Date(ts.seconds * 1000).toLocaleString();
  return String(ts);
}

function listenInvoiceRealTime() {

  if (!id) {
    alert("Invalid invoice id");
    return;
  }

  // ✅ stop old listeners
  if (unsubInvoice) unsubInvoice();
  if (unsubItems) unsubItems();
  if (unsubUser) unsubUser();

  const invRef = doc(db, "invoices", id);

  unsubInvoice = onSnapshot(invRef, (invSnap) => {

    if (!invSnap.exists()) {
      alert("Invoice not found");
      return;
    }

    const inv = invSnap.data();

    title.textContent = "Invoice #" + (inv.number ?? "—");
    date.textContent = fmtDate(inv.created_at);
    status.textContent = inv.status || "-";
    total.textContent = Number(inv.updated_total || 0).toLocaleString("en-LB") + " LBP";

    // ✅ user real-time
    if (unsubUser) unsubUser();
    const userRef = doc(db, "users", inv.user_id);

    unsubUser = onSnapshot(userRef, (userSnap) => {
      pharmacy.textContent = userSnap.exists()
        ? (userSnap.data().pharmacyname || userSnap.data().username || "Unknown")
        : "Unknown";
    });

    // ✅ items real-time
    if (unsubItems) unsubItems();

    const itemsQ = query(
      collection(db, "order_items"),
      where("order_id", "==", inv.order_id)
    );

    unsubItems = onSnapshot(itemsQ, (itemsSnap) => {

      itemsTable.innerHTML = "";

      if (itemsSnap.empty) {
        itemsTable.innerHTML = `
          <tr>
            <td colspan="4" style="text-align:center;color:#64748b;">
              No items found.
            </td>
          </tr>
        `;
        return;
      }

      itemsSnap.forEach(d => {
        const it = d.data();

        itemsTable.innerHTML += `
          <tr>
            <td>${it.name || "-"}</td>
            <td>${it.qty ?? 0}</td>
            <td>${Number(it.unit_price || 0).toLocaleString("en-LB")} LBP</td>
            <td>${Number(it.line_total || 0).toLocaleString("en-LB")} LBP</td>
          </tr>
        `;
      });
    });
  });
}

/* ✅ Mark Returned (REAL-TIME بدون Reload) */
window.markReturned = async () => {
  if (!confirm("Mark this invoice as returned?")) return;

  await updateDoc(doc(db, "invoices", id), { status: "returned" });

  alert("Invoice marked as returned ✅");
  // ✅ no reload needed (real-time will update)
};

window.goBack = () => history.back();

document.getElementById("logoutSidebar").onclick = () => {
  localStorage.clear();
  location.href = "../login.html";
};

listenInvoiceRealTime();
setupLogout(); 
