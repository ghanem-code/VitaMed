import { db } from "./firebase.js";
import { setupLogout } from "./logout.js";

import {
  collection,
  query,
  orderBy,
  onSnapshot
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

const productList = document.getElementById("productList");
const searchBox = document.getElementById("searchBox");

let allProducts = [];
let unsubProducts = null; // ✅ لإيقاف الـ listener إذا احتجنا

/* ================= RENDER ================= */
function renderProducts(items) {
  productList.innerHTML = "";

  items.forEach(p => {

    const stock = typeof p.availability === "number" ? p.availability : 0;
    const isOut = stock <= 0;

    const row = document.createElement("tr");

    row.innerHTML = `
      <td>${p.name}</td>
      <td>${p.brand || "-"}</td>
      <td>${p.dosage || "-"}</td>
      <td>${Number(p.price || 0).toLocaleString("en-LB")} LBP</td>
      <td>
        <span class="status-badge ${isOut ? "bad" : "ok"}">
          ${isOut ? "out of stock" : "Available"} (${stock})
        </span>
      </td>
    `;

    productList.appendChild(row);
  });
}

/* ================= LISTEN PRODUCTS REAL-TIME ================= */
function listenProductsRealTime() {

  // ✅ stop old listener
  if (unsubProducts) unsubProducts();

  const q = query(collection(db, "products"), orderBy("name"));

  unsubProducts = onSnapshot(q, (snap) => {

    allProducts = snap.docs.map(d => ({
      id: d.id,
      ...d.data()
    }));

    // ✅ إذا في search شغّال خلّي الفلترة تضل
    const txt = (searchBox.value || "").toLowerCase().trim();

    const filtered = txt
      ? allProducts.filter(p =>
          (p.name || "").toLowerCase().includes(txt) ||
          (p.brand || "").toLowerCase().includes(txt)
        )
      : allProducts;

    renderProducts(filtered);
  });
}

/* ================= SEARCH ================= */
searchBox.oninput = () => {
  const txt = searchBox.value.toLowerCase().trim();

  renderProducts(
    allProducts.filter(p =>
      (p.name || "").toLowerCase().includes(txt) ||
      (p.brand || "").toLowerCase().includes(txt)
    )
  );
};

/* ================= INIT ================= */
listenProductsRealTime();

setupLogout(); 
;
