import { auth, db } from "./firebase.js";

import { onAuthStateChanged, signOut } from
  "https://www.gstatic.com/firebasejs/11.0.1/firebase-auth.js";

import {
  collection,
  query,
  where,
  getDocs
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

/* ================= ADMIN GUARD (BY EMAIL) ================= */
onAuthStateChanged(auth, async (user) => {

  // ✅ not logged in
  if (!user) {
    window.location.href = "../login.html";
    return;
  }

  try {
    // ✅ get logged-in email
    const email = (user.email || "").trim().toLowerCase();

    if (!email) {
      alert("⛔ Access Denied (No email)");
      await signOut(auth);
      localStorage.clear();
      window.location.href = "../login.html";
      return;
    }

    // ✅ search user in Firestore by email
    const q = query(
      collection(db, "users"),
      where("email", "==", email)
    );

    const snap = await getDocs(q);

    if (snap.empty) {
      alert("⛔ Access Denied (User not found in DB)");
      await signOut(auth);
      localStorage.clear();
      window.location.href = "../login.html";
      return;
    }

    const data = snap.docs[0].data();
    const role = String(data.role || "").toLowerCase();
    const status = (data.status === true || data.status === "true");

    // ✅ must be admin + active
    if (role !== "admin" || !status) {
      alert("⛔ Access Denied: Admin only!");
      await signOut(auth);
      localStorage.clear();
      window.location.href = "../login.html";
      return;
    }

    // ✅ allowed
    console.log("✅ Admin access granted");

  } catch (err) {
    console.log("Admin Guard Error:", err.message);
    await signOut(auth);
    localStorage.clear();
    window.location.href = "../login.html";
  }
});
