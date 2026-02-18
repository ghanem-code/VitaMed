import { db } from "./firebase.js";
import { setupLogout } from "./logout.js";
import {
  collection,
  query,
  doc,
  getDoc,
  updateDoc,
  deleteDoc,
  onSnapshot,
  where,
  runTransaction,
  serverTimestamp
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

const box = document.getElementById("invoiceContainer");
const exportBtn = document.getElementById("exportInvoices");

function money(n){
  return Number(n || 0).toLocaleString("en-LB") + " LBP";
}

function when(t){
  if(!t) return "-";
  if(t.toDate) return t.toDate().toLocaleString();
  if(t.seconds) return new Date(t.seconds * 1000).toLocaleString();
  return "-";
}

/** بيانات جاهزة للتصدير PDF */
let reportData = [];

/* ================= REAL-TIME STATE ================= */
let invoicesCache = [];                 // invoices docs
let usersCache = new Map();             // user_id -> userData
let itemsByOrderId = new Map();         // order_id -> items[]

let unsubInvoices = null;
let unsubUsers = null;
let unsubItemsMap = new Map();          // order_id -> unsub()

function clearItemsListeners(){
  unsubItemsMap.forEach(u => u && u());
  unsubItemsMap.clear();
}

/* ================= SORT HELPERS ================= */
function sortInvoices(list){
  return list.sort((a,b)=>{
    const sa = a.status || "pending";
    const sb = b.status || "pending";

    const score = s =>{
      if(s === "completed") return 3;
      if(s === "returned")  return 4;
      return 1;
    };

    if(score(sa) !== score(sb))
      return score(sa) - score(sb);

    return (b.created_at?.seconds || 0) - (a.created_at?.seconds || 0);
  });
}

/* ================= RENDER ================= */
function render(){

  box.innerHTML = "";
  reportData = [];

  if(!invoicesCache.length){
    box.innerHTML = "<p>No invoices found.</p>";
    return;
  }

  const list = sortInvoices([...invoicesCache]);

  for(const inv of list){

    const orderId = inv.order_id;
    const items = (itemsByOrderId.get(orderId) || []).filter(i => (i.qty || 0) > 0);

    // ✅ تجاهل الفاتورة الفارغة
    if(items.length === 0) continue;

    let name = "Unnamed Pharmacy";
    let customerLocation = "-";

    if(inv.user_id && usersCache.has(inv.user_id)){
      const u = usersCache.get(inv.user_id) || {};
      name = u.pharmacyname || u.name || u.username || "Unnamed Pharmacy";
      customerLocation = u.location || "-";
    }

    const s = inv.status || "pending";

    const card = document.createElement("div");
    card.className = "invoice-card";

    card.innerHTML = `
      <div class="inv-header">
        <div>
          <b>Invoice #${inv.number || "-"}</b><br>
          Pharmacy: <b>${name}</b><br>
          Location: <b>${customerLocation}</b><br>
          Date: ${when(inv.created_at)}
        </div>
        <div style="text-align:right">
          <b>${money(inv.updated_total || inv.original_total)}</b><br>
          <span class="badge status-${s}">${s}</span>
        </div>
      </div>

      <div class="items">
        ${
          items.map(i => `
            <div class="item">
              <div>
                ${i.name || "Unknown"}
                ${i.dosage ? `<small style="color:#00838f;margin-left:6px">(${i.dosage})</small>` : ""}
                x${i.qty}
              </div>
              <div>${money(i.line_total)}</div>
            </div>
          `).join("")
        }
      </div>

      <div class="actions">
        ${s !== "completed" && s !== "returned" ? `
          <button class="complete" onclick="completeInvoice('${inv.id}')">Complete</button>
        ` : ""}

        ${s !== "returned" ? `
          <button class="return" onclick="returnInvoice('${inv.id}','${inv.order_id}')">Return</button>
        ` : ""}

        <button class="delete" onclick="deleteInvoice('${inv.id}')">Delete</button>
        <button class="export-one" onclick="exportOneInvoice('${inv.id}')">Export PDF</button>
      </div>
    `;

    box.appendChild(card);

    // ✅ DATA FOR PDF
    reportData.push({
      id: inv.id,
      number: inv.number || "-",
      pharmacy: name,
      location: customerLocation,
      date: when(inv.created_at),
      status: s,
      total: inv.updated_total || inv.original_total || 0,
      items: items.map(i => ({
        label: `${i.name || "Unknown"}${i.dosage ? ` (${i.dosage})` : ""} x${i.qty}`,
        total: i.line_total || 0
      }))
    });
  }
}

/* ================= REAL-TIME LISTENERS ================= */
function listenAllRealTime(){

  // ✅ stop old
  if(unsubInvoices) unsubInvoices();
  if(unsubUsers) unsubUsers();
  clearItemsListeners();

  // ✅ users realtime (حتى الاسم + location يتحدثو فورًا)
  unsubUsers = onSnapshot(collection(db,"users"), (snap)=>{
    usersCache.clear();
    snap.forEach(d=>{
      usersCache.set(d.id, d.data());
    });
    render();
  });

  // ✅ invoices realtime
  const invQ = query(collection(db,"invoices"));
  unsubInvoices = onSnapshot(invQ, (snap)=>{

    invoicesCache = snap.docs.map(d => ({ id:d.id, ...d.data() }));

    // ✅ افتح listeners للـ items لكل order_id
    const currentOrderIds = new Set();

    invoicesCache.forEach(inv=>{
      if(!inv.order_id) return;
      currentOrderIds.add(inv.order_id);

      // إذا listener موجود ما تعيد فتحه
      if(unsubItemsMap.has(inv.order_id)) return;

      const itemsQ = query(
        collection(db,"order_items"),
        where("order_id","==", inv.order_id)
      );

      const unsubItems = onSnapshot(itemsQ, (itemsSnap)=>{
        const items = itemsSnap.docs.map(x => ({ id:x.id, ...x.data() }));
        itemsByOrderId.set(inv.order_id, items);
        render();
      });

      unsubItemsMap.set(inv.order_id, unsubItems);
    });

    // ✅ سكّر listeners للطلبات يلي اختفت
    Array.from(unsubItemsMap.keys()).forEach(orderId=>{
      if(!currentOrderIds.has(orderId)){
        unsubItemsMap.get(orderId)();
        unsubItemsMap.delete(orderId);
        itemsByOrderId.delete(orderId);
      }
    });

    render();
  });
}

/* ================= ACTIONS (NO RELOAD ✅) ================= */

window.completeInvoice = async function(id){
  await updateDoc(doc(db,"invoices",id),{
    status:"completed",
    completed_at: serverTimestamp()
  });
  alert("Invoice completed ✅");
};

window.returnInvoice = async function(id,orderId){

  const ok = confirm("Return invoice and restore stock?");
  if(!ok) return;

  // ✅ الأفضل: Transaction لإرجاع الستوك + حذف الفاتورة
  await runTransaction(db, async (transaction) => {

    // اقرأ عناصر الطلب
    const itemsQ = query(
      collection(db,"order_items"),
      where("order_id","==", orderId)
    );

    // مافي getDocs داخل transaction في SDK web، فنعمل read direct by cache كحل سريع:
    // ✅ إذا ما في cache بنجيبهم getDoc قبل transaction (بس هون بنمشي على cache)
    const cachedItems = (itemsByOrderId.get(orderId) || []).filter(x => (x.qty || 0) > 0);

    // إذا ما كان في cache لأي سبب، رجّع للـ getDoc خارج transaction
    if(cachedItems.length === 0){
      // fallback بسيط:
      // رح نخليها بدون stock restore بدل ما تكسر الصفحة
      throw new Error("Items not loaded yet, try again in 2 seconds.");
    }

    // رجّع الستوك
    for(const it of cachedItems){
      if(!it.product_id) continue;

      const prodRef = doc(db,"products", it.product_id);
      const prodSnap = await transaction.get(prodRef);

      if(!prodSnap.exists()) continue;

      const old = Number(prodSnap.data().availability || 0);
      const qty = Number(it.qty || 0);

      transaction.update(prodRef, { availability: old + qty });
    }

    // حذف الفاتورة
    const invRef = doc(db,"invoices", id);
    transaction.delete(invRef);
  });

  alert("Invoice returned & stock restored ✅");
};

window.deleteInvoice = async function(id){
  const ok = confirm("Delete this invoice permanently?");
  if(!ok) return;

  await deleteDoc(doc(db,"invoices",id));
  alert("Invoice deleted permanently ✅");
};

/* ================= EXPORT ONE INVOICE (SAME AS YOURS) ================= */

const logoImg = new Image();
logoImg.src = "../assets/images/logo_vitamed.png";

window.exportOneInvoice = async function(invoiceId){

  const inv = reportData.find(x => x.id === invoiceId);

  if (!inv) {
    alert("Invoice not found!");
    return;
  }

  const { jsPDF } = window.jspdf;
  const pdf = new jsPDF("p", "mm", "a4");
  const pageWidth = pdf.internal.pageSize.getWidth();
  const pageHeight = pdf.internal.pageSize.getHeight();

  const left = 15;
  const right = 15;
  const contentW = pageWidth - left - right;

  let y = 18;

  const logoSize = 14;
  const logoX = left;
  const logoY = y - 10;

  pdf.setFont("helvetica", "bold");
  pdf.setFontSize(20);

  if (logoImg.complete) {
    try { pdf.addImage(logoImg, "PNG", logoX, logoY, logoSize, logoSize); } catch(e){}
    pdf.text("VITAMED", logoX + logoSize + 4, y, { align: "left" });
  } else {
    pdf.text("VITAMED", left, y, { align: "left" });
  }

  pdf.setFontSize(16);
  pdf.text("INVOICE", pageWidth - right, y, { align: "right" });

  y += 6;
  pdf.setDrawColor(0);
  pdf.setLineWidth(0.3);
  pdf.line(left, y, pageWidth - right, y);

  y += 10;

  pdf.setFont("helvetica", "normal");
  pdf.setFontSize(11);

  pdf.setFont("helvetica", "bold");
  pdf.text("Bill To:", left, y);
  pdf.setFont("helvetica", "normal");
  pdf.text(inv.pharmacy, left, y + 6);

  pdf.setFont("helvetica", "bold");
  pdf.text("Location:", left, y + 12);
  pdf.setFont("helvetica", "normal");
  pdf.text(String(inv.location || "-"), left + 22, y + 12);

  const infoX = pageWidth - right - 70;
  pdf.setFont("helvetica", "bold");
  pdf.text("Invoice #:", infoX, y);
  pdf.setFont("helvetica", "normal");
  pdf.text(String(inv.number), infoX + 28, y);

  pdf.setFont("helvetica", "bold");
  pdf.text("Date:", infoX, y + 6);
  pdf.setFont("helvetica", "normal");
  pdf.text(String(inv.date), infoX + 28, y + 6);

  pdf.setFont("helvetica", "bold");
  pdf.text("Status:", infoX, y + 12);
  pdf.setFont("helvetica", "normal");
  pdf.text(String(inv.status), infoX + 28, y + 12);

  y += 28;
  pdf.setLineWidth(0.3);
  pdf.line(left, y, pageWidth - right, y);

  y += 10;

  const colItemX = left;
  const colQtyX  = left + contentW * 0.72;
  const colTotalX= pageWidth - right;

  pdf.setFont("helvetica", "bold");
  pdf.setFontSize(11);
  pdf.text("Item", colItemX, y);
  pdf.text("Qty", colQtyX, y, { align: "right" });
  pdf.text("Total", colTotalX, y, { align: "right" });

  y += 4;
  pdf.setLineWidth(0.2);
  pdf.line(left, y, pageWidth - right, y);

  y += 8;
  pdf.setFont("helvetica", "normal");
  pdf.setFontSize(10);

  for (const it of inv.items) {
    if (y > pageHeight - 45) {
      pdf.addPage();
      y = 20;
    }

    let qty = "";
    let itemText = it.label;

    const m = String(it.label).match(/x(\d+)\s*$/i);
    if (m) {
      qty = m[1];
      itemText = String(it.label).replace(/x(\d+)\s*$/i, "").trim();
    }

    const wrapW = (colQtyX - 8) - colItemX;
    const wrappedItem = pdf.splitTextToSize(itemText, wrapW);

    pdf.text(wrappedItem, colItemX, y);
    pdf.text(qty ? qty : "", colQtyX, y, { align: "right" });
    pdf.text(money(it.total), colTotalX, y, { align: "right" });

    y += (wrappedItem.length * 6);
    pdf.setDrawColor(220);
    pdf.setLineWidth(0.15);
    pdf.line(left, y - 2, pageWidth - right, y - 2);
  }

  y += 8;
  if (y > pageHeight - 35) {
    pdf.addPage();
    y = 30;
  }

  pdf.setDrawColor(0);
  pdf.setLineWidth(0.3);

  const boxW = 70;
  const boxX = pageWidth - right - boxW;
  const boxY = y;

  pdf.roundedRect(boxX, boxY, boxW, 18, 2, 2);

  pdf.setFont("helvetica", "bold");
  pdf.setFontSize(12);
  pdf.text("TOTAL", boxX + 6, boxY + 7);

  pdf.setFont("helvetica", "normal");
  pdf.setFontSize(12);
  pdf.text(money(inv.total), boxX + boxW - 6, boxY + 14, { align: "right" });

  pdf.setFont("helvetica", "normal");
  pdf.setFontSize(10);
  pdf.text("Customer Signature: ________________________________", left, pageHeight - 18);
  pdf.text("VitaMed System", pageWidth - right, pageHeight - 18, { align: "right" });

  pdf.save(`Invoice_${inv.number}.pdf`);
};

/* ================= EXPORT ALL (SAME AS YOURS) ================= */

exportBtn.onclick = () => {
  if (!reportData.length) {
    alert("No invoices to export.");
    return;
  }

  const { jsPDF } = window.jspdf;
  const pdf = new jsPDF("p", "mm", "a4");
  const pageWidth = pdf.internal.pageSize.getWidth();

  if (logoImg.complete) {
    try { pdf.addImage(logoImg, "PNG", pageWidth / 2 - 12, 10, 24, 24); } catch(e){}
  }

  pdf.setFont("helvetica", "bold");
  pdf.setFontSize(18);
  pdf.text("VITAMED", pageWidth / 2, 42, { align: "center" });

  pdf.setFontSize(13);
  pdf.setFont("helvetica", "normal");
  pdf.text("Invoices Report", pageWidth / 2, 50, { align: "center" });

  pdf.setDrawColor(0, 188, 212);
  pdf.setLineWidth(0.8);
  pdf.line(20, 54, pageWidth - 20, 54);

  pdf.setFontSize(10);
  pdf.text(`Export Date: ${new Date().toLocaleString()}`, 20, 60);
  pdf.text(`Total Invoices: ${reportData.length}`, 20, 66);

  let y = 78;

  reportData.forEach((inv, index) => {

    const estimatedHeight = 24 + inv.items.length * 6 + 10;
    if (y + estimatedHeight > 270) {
      pdf.addPage();
      y = 30;
    }

    const cardTop = y - 8;

    pdf.setFontSize(11);
    pdf.setFont("helvetica", "bold");
    pdf.text(`${index + 1}. Invoice #${inv.number}`, 20, y);

    pdf.setFontSize(9);
    pdf.setFont("helvetica", "normal");
    pdf.text(inv.date, pageWidth - 55, y);

    pdf.text(`Pharmacy: ${inv.pharmacy}`, 20, y + 7);
    pdf.text(`Location: ${inv.location || "-"}`, 20, y + 13);
    pdf.text(`Status: ${inv.status}`, 20, y + 19);
    pdf.text(`Total: ${money(inv.total)}`, pageWidth - 55, y + 19);

    let yItems = y + 27;
    pdf.setFontSize(9);

    inv.items.forEach(it => {
      if (yItems > 270) {
        pdf.addPage();
        yItems = 30;
      }

      const line = `${it.label}  -  ${money(it.total)}`;
      const wrapped = pdf.splitTextToSize(line, pageWidth - 40);
      pdf.text(wrapped, 22, yItems);
      yItems += 6;
    });

    const cardHeight = (yItems - y) + 14;
    pdf.setDrawColor(210);
    pdf.roundedRect(15, cardTop, pageWidth - 30, cardHeight, 4, 4);

    const cardBottom = cardTop + cardHeight;
    y = cardBottom + 8;
  });

  const pages = pdf.internal.getNumberOfPages();
  for (let i = 1; i <= pages; i++) {
    pdf.setPage(i);
    pdf.setFontSize(9);
    pdf.text(`Page ${i} / ${pages}`, pageWidth / 2, 287, { align: "center" });
    pdf.text("VitaMed System - Invoices", 20, 287);
  }

  pdf.save("VitaMed_Invoices_Report.pdf");
};

/* ================= INIT REAL-TIME ================= */
listenAllRealTime();

setupLogout();
