import { db } from "./firebase.js";
import { setupLogout } from "./logout.js";
import {
  collection,
  orderBy,
  query,
  deleteDoc,
  doc,
  onSnapshot
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

const list = document.getElementById("feedbackList");
const count = document.getElementById("count");
const searchBox = document.getElementById("searchBox");
const rateFilter = document.getElementById("rateFilter");
const exportPDF = document.getElementById("exportPDF");

let allFeedback = [];
let unsubFeedback = null;

function formatDate(ts){
  if(!ts) return "-";
  if(ts.toDate) return ts.toDate().toLocaleString();
  if(ts.seconds) return new Date(ts.seconds * 1000).toLocaleString();
  return "-";
}

function stars(n){
  n = Number(n || 0);
  return "★".repeat(n) + "☆".repeat(5 - n);
}

/* ✅ REAL-TIME LISTENER */
function listenFeedbackRealTime(){

  if(unsubFeedback) unsubFeedback();

  const q = query(collection(db,"feedback"), orderBy("created_at","desc"));

  unsubFeedback = onSnapshot(q, (snap) => {

    allFeedback = snap.docs.map(d => ({ id:d.id, ...d.data() }));

    renderFeedback(); // ✅ keep filter/search
  });
}

function renderFeedback(){

  const text = (searchBox.value || "").toLowerCase().trim();
  const rate = rateFilter.value;

  let filtered = allFeedback.filter(f=> {
    const name = (f.pharmacyname || "").toLowerCase();
    const matchName = name.includes(text);
    const matchRate = rate === "all" || String(f.rating) === rate;
    return matchName && matchRate;
  });

  count.textContent = filtered.length;
  list.innerHTML = "";

  if(filtered.length === 0){
    list.innerHTML = "<p>No feedback found</p>";
    return;
  }

  filtered.forEach(f => {
    const card = document.createElement("div");
    card.className = "feedback-card";

    card.innerHTML = `
      <div class="fb-header">
        <div class="fb-name">${f.pharmacyname || "Unknown"}</div>
        <div class="fb-date">${formatDate(f.created_at)}</div>
      </div>

      <div class="fb-rating">${stars(f.rating || 0)}</div>
      <div class="fb-message">${f.message || "-"}</div>

      ${f.image_url ? `<img src="${f.image_url}" class="fb-image">` : ""}

      <div class="fb-actions">
        <button onclick="deleteFeedback('${f.id}')">Delete</button>
      </div>
    `;

    list.appendChild(card);
  });
}

/* ✅ DELETE (REAL-TIME بدون تعديل يدوي) */
window.deleteFeedback = async function(id){
  const ok = confirm("Delete this feedback?");
  if(!ok) return;

  await deleteDoc(doc(db,"feedback",id));
  alert("Feedback deleted ✅");
  // ✅ no need to update list manually (snapshot updates)
};

/* ✅ FILTER */
searchBox.oninput = renderFeedback;
rateFilter.onchange = renderFeedback;

/* ✅ LOGOUT */
document.getElementById("logoutSidebar").onclick = () => {
  localStorage.clear();
  location.href = "../login.html";
};

/* ✅ EXPORT PDF (same as your code) */
exportPDF.onclick = async () => {

  const { jsPDF } = window.jspdf;
  const pdf = new jsPDF();
  const pageWidth = pdf.internal.pageSize.width;

  const logo = new Image();
  logo.src = "../assets/images/logo_vitamed.png";

  await new Promise(resolve => logo.onload = resolve);

  pdf.addImage(logo, "PNG", pageWidth/2 - 15, 10, 30, 30);

  pdf.setFontSize(18);
  pdf.setFont("helvetica","bold");
  pdf.text("VITAMED", pageWidth/2, 50, {align:"center"});

  pdf.setFontSize(13);
  pdf.setFont("helvetica","normal");
  pdf.text("Customer Feedback Report", pageWidth/2, 58, {align:"center"});

  pdf.setDrawColor(0,188,212);
  pdf.setLineWidth(0.8);
  pdf.line(20, 62, pageWidth-20, 62);

  pdf.setFontSize(10);
  pdf.text(`Export Date: ${new Date().toLocaleString()}`, 20, 68);
  pdf.text(`Total Feedback: ${allFeedback.length}`, 20, 74);

  let y = 88;

  allFeedback.forEach((f,index)=>{

    if(y > 250){
      pdf.addPage();
      y = 30;
    }

    pdf.setDrawColor(200);
    pdf.roundedRect(15, y-8, pageWidth-30, 38, 4, 4);

    pdf.setFontSize(12);
    pdf.setFont("helvetica","bold");
    pdf.text(`${index+1}. ${f.pharmacyname || "Unknown Pharmacy"}`, 20, y);

    pdf.setFontSize(9);
    pdf.setFont("helvetica","normal");
    pdf.text(formatDate(f.created_at), pageWidth-50, y);

    pdf.setFontSize(10);
    pdf.setFont("helvetica","bold");
    pdf.text(`Rating: ${f.rating || 0} / 5`, 20, y+8);

    pdf.setFontSize(10);
    pdf.setFont("helvetica","normal");

    const msg = pdf.splitTextToSize(f.message || "-", pageWidth-50);
    pdf.text(msg, 22, y+16);

    y += 48;
  });

  const pages = pdf.internal.getNumberOfPages();

  for(let i=1;i<=pages;i++){
    pdf.setPage(i);
    pdf.setFontSize(9);
    pdf.text(`Page ${i} / ${pages}`, pageWidth/2, 287, {align:"center"});
    pdf.text("VitaMed System - Confidential", 20, 287);
  }

  pdf.save("VitaMed_Feedback_Report.pdf");
};

/* ✅ INIT REAL-TIME */
listenFeedbackRealTime();

setupLogout();
