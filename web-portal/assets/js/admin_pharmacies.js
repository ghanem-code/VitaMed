import { db } from "./firebase.js";
import { setupLogout } from "./logout.js";
import {
  collection,
  doc,
  updateDoc,
  onSnapshot
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

/* ================= ELEMENTS ================= */
const phTable   = document.getElementById("phTable");
const loading   = document.getElementById("loading");
const searchBox = document.getElementById("searchBox");
const exportBtn = document.getElementById("exportPhBtn");

let pharmacies = [];
let unsubUsers = null;

/* ================= RENDER TABLE ================= */
function renderTable(list) {

  phTable.innerHTML = "";

  if (!list.length) {
    phTable.innerHTML =
      `<tr><td colspan="6" style="text-align:center">No pharmacies found</td></tr>`;
    return;
  }

  list.forEach(p => {

    const tr = document.createElement("tr");

    tr.innerHTML = `
      <td>${p.pharmacy}</td>
      <td>${p.owner}</td>
      <td>${p.phone}</td>
      <td>${p.location}</td>

      <td>
        <span class="badge ${p.status ? "ok" : "bad"}">
          ${p.status ? "Active" : "Deactivated"}
        </span>
      </td>

      <td>
        ${
          p.status
            ? `<button class="action block" onclick="deactivateUser('${p.id}')">Deactivate</button>`
            : `<button class="action unblock" onclick="activateUser('${p.id}')">Activate</button>`
        }
      </td>
    `;

    phTable.appendChild(tr);
  });
}

/* ================= FILTER (SEARCH) ================= */
function filter() {
  const t = (searchBox.value || "").toLowerCase().trim();

  renderTable(
    pharmacies.filter(p =>
      (p.pharmacy || "").toLowerCase().includes(t) ||
      (p.owner || "").toLowerCase().includes(t) ||
      (p.phone || "").toLowerCase().includes(t) ||
      (p.location || "").toLowerCase().includes(t)
    )
  );
}

searchBox.oninput = filter;

/* ================= REAL-TIME LISTENER ================= */
function listenPharmaciesRealTime() {

  loading.style.display = "block";
  phTable.innerHTML = "";
  pharmacies = [];

  if (unsubUsers) unsubUsers();

  unsubUsers = onSnapshot(collection(db, "users"), (snap) => {

    pharmacies = [];

    snap.forEach(d => {
      const u = d.data();

      // ✅ فقط الصيدليات (pharmacy users)
      if (!u.pharmacyname) return;

      pharmacies.push({
        id: d.id,
        pharmacy: u.pharmacyname || "-",
        owner: u.username || "-",
        phone: u.phone || "-",
        location: u.location || "-",   // ✅ location
        status: u.status !== false     // ✅ true by default
      });
    });

    loading.style.display = "none";

    // ✅ keep search active
    filter();
  });
}

/* ================= ACTIONS ================= */
window.deactivateUser = async function(id){
  await updateDoc(doc(db, "users", id), { status: false });
  // ✅ no reload (real-time updates automatically)
};

window.activateUser = async function(id){
  await updateDoc(doc(db, "users", id), { status: true });
  // ✅ no reload (real-time updates automatically)
};

/* ================= EXPORT PDF (SAME CODE) ================= */
// ✅ Logo for PDF (Pharmacies)
const logoImg = new Image();
logoImg.src = "../assets/images/logo_vitamed.png";

exportBtn.onclick = () => {

  if (!pharmacies.length) {
    alert("No pharmacies to export");
    return;
  }

  const { jsPDF } = window.jspdf;
  const pdf = new jsPDF("p", "mm", "a4");

  const pageWidth  = pdf.internal.pageSize.getWidth();
  const pageHeight = pdf.internal.pageSize.getHeight();

  const left  = 14;
  const right = 14;

  const tableX = left;
  const tableW = pageWidth - left - right;

  const colW = {
    pharmacy: 46,
    owner: 35,
    phone: 28,
    location: 52,
    status: tableW - (46 + 35 + 28 + 52)
  };

  const colX = {
    pharmacy: tableX,
    owner: tableX + colW.pharmacy,
    phone: tableX + colW.pharmacy + colW.owner,
    location: tableX + colW.pharmacy + colW.owner + colW.phone,
    status: tableX + colW.pharmacy + colW.owner + colW.phone + colW.location
  };

  const rowPaddingY = 2.5;
  const lineH = 5.5;
  const headerH = 10;

  function drawCell(x, y, w, h) {
    pdf.setDrawColor(210);
    pdf.setLineWidth(0.25);
    pdf.rect(x, y, w, h);
  }

  function wrapText(text, width) {
    return pdf.splitTextToSize(String(text || "-"), width - 4);
  }

  function drawFooter(pageNum, totalPages) {
    pdf.setFont("helvetica", "normal");
    pdf.setFontSize(9);
    pdf.setTextColor(120);
    pdf.text(`Page ${pageNum} / ${totalPages}`, pageWidth / 2, 287, { align: "center" });
    pdf.text("VitaMed System - Pharmacies", left, 287);
  }

  function drawTableHeader(startY) {
    pdf.setFillColor(240, 250, 252);
    pdf.rect(tableX, startY, tableW, headerH, "F");

    pdf.setTextColor(0);
    pdf.setFont("helvetica", "bold");
    pdf.setFontSize(10);

    pdf.text("Pharmacy", colX.pharmacy + 2, startY + 7);
    pdf.text("Owner", colX.owner + 2, startY + 7);
    pdf.text("Phone", colX.phone + 2, startY + 7);
    pdf.text("Location", colX.location + 2, startY + 7);
    pdf.text("Status", colX.status + colW.status - 2, startY + 7, { align: "right" });

    drawCell(colX.pharmacy, startY, colW.pharmacy, headerH);
    drawCell(colX.owner, startY, colW.owner, headerH);
    drawCell(colX.phone, startY, colW.phone, headerH);
    drawCell(colX.location, startY, colW.location, headerH);
    drawCell(colX.status, startY, colW.status, headerH);

    return startY + headerH;
  }

  let y = 14;

  if (logoImg.complete) {
    try {
      pdf.addImage(logoImg, "PNG", pageWidth / 2 - 12, 8, 24, 24);
    } catch (e) {}
  }

  pdf.setFont("helvetica", "bold");
  pdf.setFontSize(18);
  pdf.setTextColor(0);
  pdf.text("VITAMED", pageWidth / 2, 42, { align: "center" });

  pdf.setFont("helvetica", "normal");
  pdf.setFontSize(13);
  pdf.text("Pharmacies Report", pageWidth / 2, 50, { align: "center" });

  pdf.setDrawColor(0, 188, 212);
  pdf.setLineWidth(0.8);
  pdf.line(left, 54, pageWidth - right, 54);

  pdf.setFontSize(10);
  pdf.setTextColor(0);
  pdf.text(`Export Date: ${new Date().toLocaleString()}`, left, 62);
  pdf.text(`Total Pharmacies: ${pharmacies.length}`, left, 68);

  y = 76;
  y = drawTableHeader(y);

  pdf.setFont("helvetica", "normal");
  pdf.setFontSize(9);

  for (const p of pharmacies) {

    const tPharmacy  = wrapText(p.pharmacy, colW.pharmacy);
    const tOwner     = wrapText(p.owner, colW.owner);
    const tPhone     = wrapText(p.phone, colW.phone);
    const tLocation  = wrapText(p.location, colW.location);

    const maxLines = Math.max(tPharmacy.length, tOwner.length, tPhone.length, tLocation.length, 1);
    const rowH = (maxLines * lineH) + rowPaddingY * 2;

    if (y + rowH > pageHeight - 18) {
      pdf.addPage();
      y = 20;
      y = drawTableHeader(y);
      pdf.setFont("helvetica", "normal");
      pdf.setFontSize(9);
    }

    drawCell(colX.pharmacy, y, colW.pharmacy, rowH);
    drawCell(colX.owner, y, colW.owner, rowH);
    drawCell(colX.phone, y, colW.phone, rowH);
    drawCell(colX.location, y, colW.location, rowH);
    drawCell(colX.status, y, colW.status, rowH);

    pdf.setTextColor(20);
    pdf.text(tPharmacy, colX.pharmacy + 2, y + rowPaddingY + 4);
    pdf.text(tOwner, colX.owner + 2, y + rowPaddingY + 4);
    pdf.text(tPhone, colX.phone + 2, y + rowPaddingY + 4);
    pdf.text(tLocation, colX.location + 2, y + rowPaddingY + 4);

    // ✅ updated status text
    const statusText = p.status ? "Active" : "Deactivated";

    const badgeW = 22;
    const badgeH = 7;
    const badgeX = colX.status + colW.status - badgeW - 2;
    const badgeY = y + (rowH / 2) - (badgeH / 2);

    if (p.status) pdf.setFillColor(46, 125, 50);
    else pdf.setFillColor(198, 40, 40);

    pdf.roundedRect(badgeX, badgeY, badgeW, badgeH, 2, 2, "F");

    pdf.setFont("helvetica", "bold");
    pdf.setFontSize(9);
    pdf.setTextColor(255);
    pdf.text(statusText, badgeX + badgeW / 2, badgeY + 5, { align: "center" });

    pdf.setFont("helvetica", "normal");
    pdf.setFontSize(9);
    pdf.setTextColor(0);

    y += rowH;
  }

  const pages = pdf.internal.getNumberOfPages();
  for (let i = 1; i <= pages; i++) {
    pdf.setPage(i);
    drawFooter(i, pages);
  }

  pdf.save("VitaMed_Pharmacies_Report.pdf");
};

/* ================= INIT ================= */
listenPharmaciesRealTime();
setupLogout();
