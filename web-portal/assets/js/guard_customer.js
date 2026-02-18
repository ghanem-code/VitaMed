import { auth, db } from "./firebase.js";
console.log("✅ Guard customer loaded");


import { doc, getDoc } from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

auth.onAuthStateChanged(async (user) => {

  if (!user) {
    window.location.href = "../login.html";
    return;
  }

  const ref = doc(db, "users", user.uid);
  const snap = await getDoc(ref);

  if (!snap.exists()) {
    await auth.signOut();
    window.location.href = "../login.html";
    return;
  }

  const data = snap.data();

  // ممنوع Admin يدخل Customer
  if (data.role === "admin") {
    window.location.href = "../Admin/admin.html";
    return;
  }

  // لازم يكون Active
  if (data.status === false) {
    alert("⛔ Your account is blocked.");
    await auth.signOut();
    window.location.href = "../login.html";
    return;
  }

  console.log("✅ Customer access granted");
});
