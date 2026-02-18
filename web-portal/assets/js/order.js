import { db, auth } from "./firebase.js";
import { setupLogout } from "./logout.js";

import {
  collection,
  query,
  orderBy,
  doc,
  runTransaction,
  onSnapshot,
  serverTimestamp
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

let products = [];
let cart = [];

/* ================= ELEMENTS ================= */
const productList   = document.getElementById("productList");
const searchBox     = document.getElementById("searchBox");
const cartPanel     = document.getElementById("cartPanel");
const cartItemsDiv  = document.getElementById("cartItems");
const totalPriceDiv = document.getElementById("totalPrice");
const openCart      = document.getElementById("openCart");
const closeCart     = document.getElementById("closeCart");
const placeOrderBtn = document.getElementById("placeOrderBtn");

// ✅ NEW
const cartCount = document.getElementById("cartCount");

/* ================= REAL-TIME LISTENER ================= */
let unsubProducts = null;

/* ================= LISTEN PRODUCTS REAL-TIME ================= */
function listenProductsRealTime() {

  if (unsubProducts) unsubProducts();

  const q = query(collection(db, "products"), orderBy("name"));

  unsubProducts = onSnapshot(q, (snap) => {

    products = snap.docs.map(d => ({
      pid: d.id,
      ...d.data()
    }));

    // ✅ update cart stocks real-time
    cart.forEach(ci => {
      const p = products.find(x => x.pid === ci.pid);
      if (p) {
        ci.stock = typeof p.availability === "number" ? p.availability : 0;
      }
    });

    const t = (searchBox.value || "").toLowerCase().trim();

    const filtered = t
      ? products.filter(p =>
          (p.name || "").toLowerCase().includes(t) ||
          (p.brand || "").toLowerCase().includes(t)
        )
      : products;

    renderProducts(filtered);
    updateCart();
  });
}

/* ================= RENDER PRODUCTS ================= */
function renderProducts(list) {
  productList.innerHTML = "";

  list.forEach(p => {

    const stock = typeof p.availability === "number" ? p.availability : 0;
    const out   = stock <= 0;

    const row = document.createElement("tr");
    row.dataset.pid = p.pid;

    row.innerHTML = `
      <td>${p.name}</td>
      <td>${p.brand || "-"}</td>
      <td>${p.dosage || "-"}</td>
      <td>${Number(p.price || 0).toLocaleString("en-LB")} LBP</td>
      <td class="${out ? "stock-out" : ""}">
        ${out ? "0 (out of stock)" : stock}
      </td>
      <td>
        <input type="number" value="1" min="1"
          class="qty-input"
          data-qty="${p.pid}"
          ${out ? "disabled" : ""}>
      </td>
      <td>
        <button class="add-btn"
          data-id="${p.pid}"
          ${out ? "disabled" : ""}>
          Add
        </button>
      </td>
    `;

    productList.appendChild(row);
  });
}

/* ================= ADD TO CART ================= */
productList.addEventListener("click", e => {
  if (e.target.classList.contains("add-btn")) {
    addToCart(e.target.dataset.id);
  }
});

function addToCart(id) {

  const product = products.find(p => p.pid === id);
  if (!product) return;

  const stock = typeof product.availability === "number" ? product.availability : 0;
  if (stock <= 0) return alert("Unavailable");

  const input = document.querySelector(`input[data-qty="${id}"]`);
  let qty = parseInt(input.value, 10) || 1;
  qty = Math.min(qty, stock);

  const exists = cart.find(i => i.pid === id);

  if (exists) {
    exists.qty = Math.min(exists.qty + qty, stock);
    exists.line_total = exists.qty * exists.price;
  } else {
    cart.push({
      pid: id,
      name: product.name,
      brand: product.brand || "",
      dosage: product.dosage || "",
      qty,
      price: product.price || 0,
      line_total: qty * (product.price || 0),
      stock
    });
  }

  updateCart();
}

/* ================= UPDATE CART ================= */
function updateCart() {

  cartItemsDiv.innerHTML = "";
  let total = 0;

  // ✅ stock sync
  cart.forEach(item => {
    const liveStock = typeof item.stock === "number" ? item.stock : 0;

    if (liveStock <= 0) {
      item.qty = 0;
      item.line_total = 0;
    } else if (item.qty > liveStock) {
      item.qty = liveStock;
      item.line_total = item.qty * item.price;
    }
  });

  cart = cart.filter(x => x.qty > 0);

  cart.forEach((item, index) => {

    total += item.line_total;

    cartItemsDiv.innerHTML += `
      <div class="cart-item-box">
        <strong>
          ${item.name}
          ${item.brand ? `<small> - ${item.brand}</small>` : ""}
          ${item.dosage ? `<small> (${item.dosage})</small>` : ""}
        </strong>

        <div class="qty-controller">
          <button class="qty-btn minus" data-index="${index}">−</button>
          <span class="qty-num">${item.qty}</span>
          <button class="qty-btn plus" data-index="${index}">+</button>
        </div>

        <span class="delete-item" data-index="${index}">✕</span>

        <div class="line-price">
          ${Number(item.line_total).toLocaleString("en-LB")} LBP
        </div>
      </div>
    `;
  });

  totalPriceDiv.textContent =
    Number(total).toLocaleString("en-LB") + " LBP";

  // ✅ NEW: cart count
  if (cartCount) {
    const count = cart.reduce((sum, x) => sum + (x.qty || 0), 0);
    cartCount.textContent = count;
  }
}

/* ================= CART CONTROLS (PLUS / MINUS / DELETE) ================= */
cartItemsDiv.addEventListener("click", (e) => {

  const idx = e.target.dataset.index;
  if (idx === undefined) return;

  const i = parseInt(idx, 10);
  if (!cart[i]) return;

  // ✅ minus
  if (e.target.classList.contains("minus")) {
    cart[i].qty -= 1;
    if (cart[i].qty <= 0) cart.splice(i, 1);
    else cart[i].line_total = cart[i].qty * cart[i].price;
    updateCart();
  }

  // ✅ plus
  if (e.target.classList.contains("plus")) {
    const liveStock = cart[i].stock || 0;
    if (cart[i].qty < liveStock) {
      cart[i].qty += 1;
      cart[i].line_total = cart[i].qty * cart[i].price;
      updateCart();
    }
  }

  // ✅ delete
  if (e.target.classList.contains("delete-item")) {
    cart.splice(i, 1);
    updateCart();
  }
});

/* ================= SEARCH ================= */
searchBox.oninput = () => {
  const t = searchBox.value.toLowerCase().trim();

  renderProducts(
    products.filter(p =>
      (p.name || "").toLowerCase().includes(t) ||
      (p.brand || "").toLowerCase().includes(t)
    )
  );
};

/* ================= PLACE ORDER + INVOICE ================= */
placeOrderBtn.onclick = async () => {

  if (cart.length === 0) return alert("Cart is empty");

  const user = auth.currentUser;
  if (!user) return alert("Login again");

  await runTransaction(db, async transaction => {

    let total = 0;
    cart.forEach(i => total += i.line_total);

    const orderRef   = doc(collection(db, "orders"));
    const invoiceRef = doc(collection(db, "invoices"));

    /* ===== ORDER ===== */
    transaction.set(orderRef, {
      id: orderRef.id,
      user_id: user.uid,
      total,
      status: "completed",
      created_at: serverTimestamp()
    });

    /* ===== ORDER ITEMS + UPDATE STOCK ===== */
    for (const item of cart) {

      const itemRef = doc(collection(db, "order_items"));

      transaction.set(itemRef, {
        id: itemRef.id,
        order_id: orderRef.id,
        product_id: item.pid,
        name: item.name,
        brand: item.brand,
        dosage: item.dosage,
        qty: item.qty,
        unit_price: item.price,
        line_total: item.line_total
      });

      const prodRef = doc(db, "products", item.pid);

      transaction.update(prodRef, {
        availability: item.stock - item.qty
      });
    }

    /* ===== INVOICE ===== */
    transaction.set(invoiceRef, {
      id: invoiceRef.id,
      number: Date.now(),
      order_id: orderRef.id,
      user_id: user.uid,

      original_total: total,
      updated_total: total,
      return_total: 0,

      status: "pending",
      created_at: serverTimestamp()
    });
  });

  alert("Order placed successfully ✅");

  cart = [];
  updateCart();
};

/* ================= INIT ================= */
listenProductsRealTime();

openCart.onclick  = () => cartPanel.style.right = "0";
closeCart.onclick = () => cartPanel.style.right = "-420px";

setupLogout();
