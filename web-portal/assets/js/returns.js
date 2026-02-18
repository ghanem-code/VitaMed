import { db, auth } from "./firebase.js";
import { setupLogout } from "./logout.js";
import {
  collection,
  query,
  where,
  doc,
  runTransaction,
  onSnapshot
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

const invoiceList = document.getElementById("invoiceList");
const loading = document.getElementById("loading");

// ✅ Global cache (مهم للـ modal حتى ما يصير null بسبب real-time rerender)
let invoicesCache = [];

// ✅ نخزن الـ unsubscribers لنوقف listeners لما نغير user أو نعمل logout
let unsubscribeInvoices = null;
let unsubscribeItemsMap = new Map(); // key = invoiceId => unsubscribe()

/* =================== ✅ RETURN MODAL HANDLER =================== */
/* لازم تكون ضايف HTML للـ modal بالصفحة */
const returnModalOverlay = document.getElementById("returnModalOverlay");
const closeReturnModalBtn = document.getElementById("closeReturnModalBtn");
const cancelReturnModalBtn = document.getElementById("cancelReturnModalBtn");
const confirmReturnModalBtn = document.getElementById("confirmReturnModalBtn");

const modalItemName = document.getElementById("modalItemName");
const modalPurchasedQty = document.getElementById("modalPurchasedQty");
const modalReturnQty = document.getElementById("modalReturnQty");
const modalHintText = document.getElementById("modalHintText");

// ✅ نخزن IDs بدل objects (real-time safe)
let selectedInvoiceId = null;
let selectedItemId = null;

function openReturnModal(inv, item) {
  if (!returnModalOverlay) {
    alert("Modal not found in HTML.");
    return;
  }

  // ✅ store IDs
  selectedInvoiceId = inv.id;
  selectedItemId = item.id;

  modalItemName.textContent = item.name || "Medicine";
  modalPurchasedQty.textContent = item.qty || 0;

  modalReturnQty.value = 1;
  modalReturnQty.min = 1;
  modalReturnQty.max = item.qty;

  modalHintText.classList.remove("error");
  modalHintText.textContent = `Choose a quantity between 1 and ${item.qty}.`;

  returnModalOverlay.classList.add("show");
  setTimeout(() => modalReturnQty.focus(), 50);
}

function closeReturnModal() {
  if (!returnModalOverlay) return;

  returnModalOverlay.classList.remove("show");

  selectedInvoiceId = null;
  selectedItemId = null;
}

function validateReturnQtyInput() {
  // ✅ نجيب item الحالي من الكاش
  const inv = invoicesCache.find((x) => x.id === selectedInvoiceId);
  const item = inv?.items?.find((i) => i.id === selectedItemId);

  const maxQty = item?.qty || 0;
  const value = parseInt(modalReturnQty.value, 10);

  if (isNaN(value) || value <= 0) {
    modalHintText.classList.add("error");
    modalHintText.textContent = "Please enter a valid quantity.";
    return null;
  }

  if (value > maxQty) {
    modalHintText.classList.add("error");
    modalHintText.textContent = `Return quantity cannot be more than ${maxQty}.`;
    return null;
  }

  modalHintText.classList.remove("error");
  modalHintText.textContent = `You will return ${value} item(s).`;
  return value;
}

// events
if (closeReturnModalBtn) closeReturnModalBtn.onclick = closeReturnModal;
if (cancelReturnModalBtn) cancelReturnModalBtn.onclick = closeReturnModal;

// click outside modal box closes it
if (returnModalOverlay) {
  returnModalOverlay.addEventListener("click", (e) => {
    if (e.target === returnModalOverlay) closeReturnModal();
  });
}

// ESC closes modal
document.addEventListener("keydown", (e) => {
  if (!returnModalOverlay) return;
  if (e.key === "Escape" && returnModalOverlay.classList.contains("show")) {
    closeReturnModal();
  }
});

// live validation
if (modalReturnQty) {
  modalReturnQty.addEventListener("input", validateReturnQtyInput);
}

// confirm
if (confirmReturnModalBtn) {
  confirmReturnModalBtn.onclick = async () => {
    const qty = validateReturnQtyInput();
    if (!qty) return;

    // ✅ real-time safe get invoice/item from cache
    const inv = invoicesCache.find((x) => x.id === selectedInvoiceId);
    if (!inv) {
      alert("Invoice not found. Please try again.");
      return;
    }

    const item = (inv.items || []).find((i) => i.id === selectedItemId);
    if (!item) {
      alert("Item not found. It may have been updated. Please try again.");
      return;
    }

    closeReturnModal();

    returnSingleItem(inv, item, qty);
  };
}

/* =================== END MODAL =================== */

function clearAllItemListeners() {
  unsubscribeItemsMap.forEach((unsub) => unsub && unsub());
  unsubscribeItemsMap.clear();
}

function renderInvoices(invoicesData) {
  invoiceList.innerHTML = "";

  if (!invoicesData || invoicesData.length === 0) {
    loading.style.display = "block";
    loading.textContent = "No invoices found.";
    return;
  }

  loading.style.display = "none";

  // ✅ ترتيب: completed آخر شي + الأحدث أولاً داخل كل مجموعة
  invoicesData.sort((a, b) => {
    const sa = (a.status || "").toLowerCase();
    const sb = (b.status || "").toLowerCase();

    const score = (s) => (s === "completed" ? 2 : 1);

    if (score(sa) !== score(sb)) return score(sa) - score(sb);

    const ta = a.created_at?.seconds || 0;
    const tb = b.created_at?.seconds || 0;
    return tb - ta;
  });

  for (const inv of invoicesData) {
    const isCompleted = (inv.status || "").toLowerCase() === "completed";

    const card = document.createElement("div");
    card.className = "invoice-card";
    card.setAttribute("data-invoice-id", inv.id);

    card.innerHTML = `
      <div class="invoice-header">
        <div>
          <h3>Invoice #${inv.number}</h3>
          <small>${
            inv.created_at
              ? new Date(inv.created_at.seconds * 1000).toLocaleString()
              : ""
          }</small>
        </div>
        <strong>${inv.updated_total} LBP</strong>
      </div>

      <div class="invoice-items"></div>

      ${
        isCompleted
          ? `<div style="margin-top:10px;color:#0a7c3a;font-weight:600;">✅ Completed — return disabled</div>`
          : `<button class="cancel-full-btn">Cancel Entire Invoice</button>`
      }
    `;

    const itemsDiv = card.querySelector(".invoice-items");

    // ✅ items render
    (inv.items || []).forEach((item) => {
      const line = document.createElement("div");
      line.className = "item-line";

      line.innerHTML = `
        <span>${item.name} (x${item.qty})</span>
        <span>
          ${item.line_total} LBP 
          ${isCompleted ? "" : `<button class="return-item-btn">Return</button>`}
        </span>
      `;

      const btn = line.querySelector(".return-item-btn");
      if (btn) {
        btn.onclick = () => openReturnModal(inv, item);
      }

      itemsDiv.appendChild(line);
    });

    // ✅ زر cancel invoice
    const cancelBtn = card.querySelector(".cancel-full-btn");
    if (cancelBtn) {
      cancelBtn.onclick = () => returnFullInvoice(inv, inv.items || []);
    }

    invoiceList.appendChild(card);
  }
}

function attachOrderItemsListener(inv, onItemsUpdate) {
  if (unsubscribeItemsMap.has(inv.id)) {
    unsubscribeItemsMap.get(inv.id)();
    unsubscribeItemsMap.delete(inv.id);
  }

  const itemsQ = query(
    collection(db, "order_items"),
    where("order_id", "==", inv.order_id)
  );

  const unsub = onSnapshot(itemsQ, (itemsSnap) => {
    const items = itemsSnap.docs.map((x) => ({ id: x.id, ...x.data() }));
    onItemsUpdate(inv.id, items);
  });

  unsubscribeItemsMap.set(inv.id, unsub);
}

function loadInvoicesRealTime(uid) {
  loading.style.display = "block";
  loading.textContent = "Loading invoices...";

  if (unsubscribeInvoices) unsubscribeInvoices();
  clearAllItemListeners();

  const invoicesQ = query(
    collection(db, "invoices"),
    where("user_id", "==", uid)
  );

  unsubscribeInvoices = onSnapshot(invoicesQ, (snap) => {
    invoicesCache = snap.docs.map((d) => ({
      id: d.id,
      ...d.data(),
      items: []
    }));

    if (invoicesCache.length === 0) {
      renderInvoices([]);
      return;
    }

    invoicesCache.forEach((inv) => {
      attachOrderItemsListener(inv, (invoiceId, items) => {
        const index = invoicesCache.findIndex((x) => x.id === invoiceId);
        if (index !== -1) {
          invoicesCache[index].items = items;
        }
        renderInvoices(invoicesCache);
      });
    });

    renderInvoices(invoicesCache);
  });
}

/* =================== ✅ RETURN PARTIAL QTY (TRANSACTION) =================== */
async function returnSingleItem(invoice, item, returnQty) {
  try {
    await runTransaction(db, async (t) => {
      // ✅ product id support multiple field names
      const pid = item.product_id || item.productId || item.prod_id;

      if (!pid) {
        throw new Error("Product ID missing in order_items.");
      }

      const prodRef = doc(db, "products", pid);
      const prodSnap = await t.get(prodRef);

      const invRef = doc(db, "invoices", invoice.id);
      const invSnap = await t.get(invRef);

      const statusNow = (invSnap.data().status || "").toLowerCase();
      if (statusNow === "completed") {
        throw new Error("Return disabled: invoice is completed.");
      }

      if (!returnQty || returnQty <= 0) {
        throw new Error("Invalid return quantity.");
      }

      if (returnQty > item.qty) {
        throw new Error("Return quantity cannot be greater than purchased quantity.");
      }

      const currentStock = prodSnap.data().availability || 0;
      const newStock = currentStock + returnQty;

      const unitPrice = (item.line_total || 0) / (item.qty || 1);
      const returnAmount = unitPrice * returnQty;

      const oldUpdatedTotal = invSnap.data().updated_total || 0;
      const oldReturnTotal = invSnap.data().return_total || 0;

      const newTotal = oldUpdatedTotal - returnAmount;
      const newReturn = oldReturnTotal + returnAmount;

      t.update(prodRef, { availability: newStock });

      t.update(invRef, {
        updated_total: newTotal,
        return_total: newReturn,
        status: newTotal === 0 ? "returned" : "partially_returned"
      });

      const itemRef = doc(db, "order_items", item.id);
      const remainingQty = item.qty - returnQty;

      if (remainingQty > 0) {
        t.update(itemRef, {
          qty: remainingQty,
          line_total: unitPrice * remainingQty
        });
      } else {
        t.delete(itemRef);
      }
    });

    alert("Returned successfully ✅");
  } catch (err) {
    alert(err.message);
    console.error(err);
  }
}

/* =================== ✅ CANCEL FULL INVOICE (TRANSACTION) =================== */
async function returnFullInvoice(invoice, items) {
  if (!confirm("Are you sure you want to cancel the whole invoice?")) return;

  try {
    await runTransaction(db, async (t) => {
      const invRef = doc(db, "invoices", invoice.id);
      const invSnap = await t.get(invRef);

      const statusNow = (invSnap.data().status || "").toLowerCase();
      if (statusNow === "completed") {
        throw new Error("Return disabled: invoice is completed.");
      }

      const productReads = [];
      const productRefs = [];

      items.forEach((item) => {
        const pid = item.product_id || item.productId || item.prod_id;

        if (!pid) {
          throw new Error("Product ID missing in order_items.");
        }

        const ref = doc(db, "products", pid);
        productRefs.push(ref);
        productReads.push(t.get(ref));
      });

      const snaps = await Promise.all(productReads);

      snaps.forEach((snap, i) => {
        const item = items[i];
        const ref = productRefs[i];

        const newStock = (snap.data().availability || 0) + item.qty;
        t.update(ref, { availability: newStock });

        const itemRef = doc(db, "order_items", item.id);
        t.delete(itemRef);
      });

      t.update(invRef, {
        updated_total: 0,
        return_total: invSnap.data().original_total,
        status: "returned"
      });
    });

    alert("Invoice canceled successfully.");
  } catch (err) {
    alert(err.message);
    console.error(err);
  }
}

/* =================== ✅ AUTH =================== */
auth.onAuthStateChanged((user) => {
  if (user) {
    loadInvoicesRealTime(user.uid);
  } else {
    if (unsubscribeInvoices) unsubscribeInvoices();
    clearAllItemListeners();

    loading.style.display = "block";
    loading.textContent = "Please login again.";
    invoiceList.innerHTML = "";
  }
})
setupLogout();
;
