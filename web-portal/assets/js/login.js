import { auth, db } from "./firebase.js";

import {
  signInWithEmailAndPassword,
  sendEmailVerification,
  signOut
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-auth.js";

import {
  collection,
  query,
  where,
  getDocs,
  doc,
  updateDoc
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

window.loginUser = async function (event) {
  if (event) event.preventDefault();

  const username = document.getElementById("username").value.trim();
  const password = document.getElementById("password").value.trim();

  if (!username || !password) {
    alert("Enter username & password");
    return;
  }

  /* ================= FIND USER IN FIRESTORE ================= */
  const q = query(collection(db, "users"), where("username", "==", username));
  const snap = await getDocs(q);

  if (snap.empty) {
    alert("Username not found");
    return;
  }

  const userDoc = snap.docs[0];
  const userData = userDoc.data();
  const uid = userDoc.id;

  const email = (userData.email || "").trim();
  const role = (userData.role || "customer").toLowerCase();

  if (!email) {
    alert("No email found for this user");
    return;
  }

  /* ================= CHECK ACTIVATION ================= */
  const isActivated = (userData.status === true || userData.status === "true");
  if (!isActivated) {
    alert("⏳ Please wait for admin to activate your account.");
    return;
  }

  /* ================= LOGIN AUTH ================= */
  try {
    const res = await signInWithEmailAndPassword(auth, email, password);

    /* ================= EMAIL VERIFICATION HANDLING ================= */
    // ✅ Admin bypass: admin can enter even without verification
    if (!res.user.emailVerified && role !== "admin") {

      // ✅ send verification automatically
      await sendEmailVerification(res.user);

      // ✅ logout so he can’t use system until verified
      await signOut(auth);

      alert("📩 Verification email sent!\n⚠️ Please verify your email then login again.");
      return;
    }

    // ✅ Save login data
    localStorage.setItem("username", userData.username || username);
    localStorage.setItem("uid", uid);
    localStorage.setItem("role", role);

    // ✅ Optional sync (only if verified)
    if (res.user.emailVerified) {
      await updateDoc(doc(db, "users", uid), { emailVerified: true });
    }

    alert("Login Successful 🎉");

    /* ================= REDIRECT ================= */
    if (role === "admin") {
      window.location.href = "Admin/admin.html";
    } else {
      window.location.href = "Customer/customer.html";
    }

  } catch (err) {
    alert("Login failed: " + err.message);
  }
};

/* ================= SHOW / HIDE PASSWORD ================= */
const passInput = document.getElementById("password");
const toggleBtn = document.getElementById("togglePass");

if (toggleBtn && passInput) {
  toggleBtn.onclick = () => {
    const isHidden = passInput.type === "password";
    passInput.type = isHidden ? "text" : "password";
    toggleBtn.textContent = isHidden ? "🙈" : "👁️";
    toggleBtn.setAttribute(
      "aria-label",
      isHidden ? "Hide password" : "Show password"
    );
  };
}

/* ================= CLEAR AUTOFILL ================= */
window.addEventListener("DOMContentLoaded", () => {
  const u = document.getElementById("username");
  const p = document.getElementById("password");

  setTimeout(() => {
    if (u) u.value = "";
    if (p) p.value = "";
  }, 80);
});
