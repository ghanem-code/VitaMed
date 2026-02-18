import { db } from "./firebase.js";
import { setupLogout } from "./logout.js";
import {
  collection,
  addDoc,
  deleteDoc,
  doc,
  updateDoc,
  onSnapshot,
  query,
  orderBy,
  serverTimestamp
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

/* ============ PDF LIB ============ */
const { jsPDF } = window.jspdf || {};

/* ============ ELEMENTS ============ */
const modal = document.getElementById("modal");
const table = document.getElementById("productTable");
const loading = document.getElementById("loading");
const addBtn = document.getElementById("addBtn");
const exportBtn = document.getElementById("exportBtn");

const pName  = document.getElementById("pName");
const pBrand = document.getElementById("pBrand");
const pDose  = document.getElementById("pDose");
const pPrice = document.getElementById("pPrice");
const pStock = document.getElementById("pStock");

const searchBox   = document.getElementById("searchBox");
const filterStock = document.getElementById("filterStock");

/* ============ STATE ============ */
let editId = null;
let products = [];
let unsubProducts = null;

const colRef = collection(db, "products");

/* ✅ ADMIN ACTIVITY (NOTIFICATIONS) */
const activityRef = collection(db, "admin_activity");

/* ✅ LOG FUNCTION (LIKE ANDROID) */
async function logAdminAction(type, message, productId) {
  try {
    await addDoc(activityRef, {
      id: Date.now(),
      type: type,
      message: message,
      productId: productId || null,
      timestamp: serverTimestamp()
    });
  } catch (e) {
    console.log("Log error:", e.message);
  }
}

/* ============ HELPERS ============ */

function openModal() {
  modal.style.display = "flex";
}

function closeModal() {
  modal.style.display = "none";
  clearForm();
}

function clearForm() {
  editId = null;
  pName.value = "";
  pBrand.value = "";
  pDose.value = "";
  pPrice.value = "";
  pStock.value = "";
  document.getElementById("modalTitle").innerText = "Add Product";
}

function statusText(p) {
  const a = Number(p.availability || 0);
  if (a === 0) return "Out of stock";
  if (a <= 10) return "Low";
  return "Available";
}

function statusClass(p) {
  const a = Number(p.availability || 0);
  if (a === 0) return "out";
  if (a <= 10) return "low";
  return "ok";
}

function moneyLBP(v) {
  return Number(v || 0).toLocaleString("en-LB") + " LBP";
}

/* ============ RENDER TABLE ============ */

function render(list) {
  table.innerHTML = "";

  list.forEach(p => {
    const tr = document.createElement("tr");

    tr.innerHTML = `
      <td>${p.name}</td>
      <td>${p.brand || "-"}</td>
      <td>${p.dosage || "-"}</td>
      <td>${moneyLBP(p.price)}</td>
      <td>${p.availability ?? 0}</td>
      <td>
        <span class="status ${statusClass(p)}">
          ${statusText(p)}
        </span>
      </td>
      <td>
        <button class="action-btn edit">Edit</button>
        <button class="action-btn del">Delete</button>
      </td>
    `;

    tr.querySelector(".edit").onclick = () => editProduct(p.docId);
    tr.querySelector(".del").onclick  = () => removeProduct(p.docId);

    table.appendChild(tr);
  });
}

/* ============ FILTER (works with real-time) ============ */

function filter() {
  const t = (searchBox.value || "").toLowerCase().trim();

  let list = products.filter(p =>
    (p.name || "").toLowerCase().includes(t) ||
    (p.brand || "").toLowerCase().includes(t)
  );

  const f = filterStock.value;
  if (f === "low") list = list.filter(p => (p.availability ?? 0) > 0 && (p.availability ?? 0) <= 10);
  if (f === "out") list = list.filter(p => (p.availability ?? 0) === 0);

  render(list);
}

/* ============ LISTEN PRODUCTS REAL-TIME ============ */

function listenProductsRealTime() {
  loading.style.display = "block";
  table.innerHTML = "";

  if (unsubProducts) unsubProducts();

  // ✅ ترتيب حسب الاسم مثل قبل (اختياري)
  const q = query(colRef, orderBy("name"));

  unsubProducts = onSnapshot(q, (snap) => {
    products = snap.docs.map(d => ({
      docId: d.id,
      ...d.data()
    }));

    loading.style.display = "none";

    // ✅ keep current filter/search active
    filter();
  });
}

/* ============ CRUD (REAL-TIME) ============ */

async function saveProduct() {
  const data = {
    name: pName.value.trim(),
    brand: pBrand.value.trim(),
    dosage: pDose.value.trim(),
    price: Number(pPrice.value),
    availability: Number(pStock.value)
  };

  if (!data.name) {
    alert("Name is required");
    return;
  }

  try {
    if (editId) {
      await updateDoc(doc(db, "products", editId), data);

      // ✅ LOG EDIT (Notification)
      await logAdminAction("edit", `Admin updated ${data.name}`, editId);

    } else {
      const ref = await addDoc(colRef, data);

      // ✅ LOG ADD (Notification)
      await logAdminAction("add", `Admin added ${data.name}`, ref.id);
    }

    closeModal();
    // ✅ no loadProducts() (snapshot will refresh)
  } catch (e) {
    alert("Error: " + e.message);
  }
}

function editProduct(id) {
  const p = products.find(x => x.docId === id);
  if (!p) return;

  editId = id;
  pName.value = p.name;
  pBrand.value = p.brand || "";
  pDose.value = p.dosage || "";
  pPrice.value = p.price ?? 0;
  pStock.value = p.availability ?? 0;

  document.getElementById("modalTitle").innerText = "Edit Product";
  openModal();
}

async function removeProduct(id) {
  if (!confirm("Delete this product?")) return;

  try {
    const p = products.find(x => x.docId === id);

    await deleteDoc(doc(db, "products", id));

    // ✅ LOG DELETE (Notification)
    await logAdminAction("delete", `Admin deleted ${p?.name || "product"}`, id);

    // ✅ no loadProducts() (snapshot will refresh)
  } catch (e) {
    alert("Error: " + e.message);
  }
}

/* ============ EXPORT PDF (same) ============ */
function exportProductsPdf() {
  if (!jsPDF) {
    alert("PDF library not loaded");
    return;
  }

  if (products.length === 0) {
    alert("No products to export");
    return;
  }

  const pdf = new jsPDF({ unit: "mm", format: "a4" });
  const pageWidth = pdf.internal.pageSize.getWidth();

  const logo = new Image();
  logo.src = "../assets/images/logo_vitamed.png";

  logo.onload = () => {

    pdf.addImage(logo, "PNG", 20, 12, 18, 18);

    pdf.setFont("helvetica", "bold");
    pdf.setFontSize(22);
    pdf.text("VITAMED", 42, 24);

    pdf.setFontSize(13);
    pdf.text("Products Report", 42, 31);

    pdf.line(20, 36, pageWidth - 20, 36);

    let y = 44;
    pdf.setFontSize(10);
    pdf.text("Export Date: " + new Date().toLocaleString(), 20, y);
    y += 8;

    const colWidths = [40, 30, 25, 30, 20, 25];
    const headers = ["Name", "Brand", "Dosage", "Price (LBP)", "Stock", "Status"];

    function drawHeader() {
      let x = 20;
      pdf.setFont("helvetica", "bold");
      headers.forEach((h, i) => {
        pdf.text(h, x, y);
        x += colWidths[i];
      });
      y += 4;
      pdf.line(20, y, pageWidth - 20, y);
      y += 6;
      pdf.setFont("helvetica", "normal");
    }

    drawHeader();

    products.forEach(p => {
      if (y > 270) {
        pdf.addPage();
        y = 30;
        drawHeader();
      }

      let x = 20;
      const row = [
        p.name || "-",
        p.brand || "-",
        p.dosage || "-",
        Number(p.price || 0).toLocaleString("en-LB"),
        String(p.availability ?? 0),
        statusText(p)
      ];

      row.forEach((v, i) => {
        pdf.text(String(v), x, y);
        x += colWidths[i];
      });

      y += 6;
    });

    pdf.save("VitaMed_Products_Report.pdf");
  };

  logo.onerror = () => {
    alert("Logo not found! Check logo path.");
  };
}

/* ============ EVENTS ============ */
searchBox.oninput = filter;
filterStock.onchange = filter;
addBtn.onclick = openModal;
exportBtn.onclick = exportProductsPdf;

window.saveProduct = saveProduct;
window.closeModal = closeModal;

/* ✅ INIT REAL-TIME */
listenProductsRealTime();
setupLogout(); 
