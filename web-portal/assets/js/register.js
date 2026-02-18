import { auth, db } from "./firebase.js";
import {
  createUserWithEmailAndPassword,
  sendEmailVerification
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-auth.js";

import {
  setDoc,
  doc,
  serverTimestamp
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

window.registerUser = async function (event) {
  event.preventDefault();

  const username = document.getElementById("username").value.trim();
  const email    = document.getElementById("email").value.trim();
  const pass     = document.getElementById("password").value.trim();
  const pass2    = document.getElementById("confirmPassword").value.trim();
  const phone    = document.getElementById("phone").value.trim();
  const pharmacy = document.getElementById("pharmacy").value.trim();
  const location = document.getElementById("location").value.trim();

  if (!username || !email || !pass || !pass2 || !phone || !pharmacy || !location) {
    alert("Fill all fields");
    return;
  }

  if (pass !== pass2) {
    alert("Passwords do not match");
    return;
  }

  try {
    // 1️⃣ Create Auth user
    const res = await createUserWithEmailAndPassword(auth, email, pass);
    const uid = res.user.uid;

    // 2️⃣ Save user in Firestore
    await setDoc(doc(db, "users", uid), {
      uid,
      username,
      email,
      phone,
      pharmacyname: pharmacy,
      location,                 
      status: false,
      emailVerified: false,
      createdAt: serverTimestamp()
    });

    // 3️⃣ Email verification
    await sendEmailVerification(res.user);

    alert("🎉 Account created!\n📩 Please verify your email before logging in.");

    setTimeout(() => {
      window.location.href = "login.html";
    }, 1500);

  } catch (e) {
    alert("Registration failed: " + e.message);
  }
};

/* ===== Show / Hide Password ===== */

const passInput  = document.getElementById("password");
const pass2Input = document.getElementById("confirmPassword");
const toggleBtn1 = document.getElementById("togglePass");
const toggleBtn2 = document.getElementById("togglePass2");

toggleBtn1.onclick = () => {
  const hidden = passInput.type === "password";
  passInput.type = hidden ? "text" : "password";
  toggleBtn1.textContent = hidden ? "🙈" : "👁️";
};

toggleBtn2.onclick = () => {
  const hidden = pass2Input.type === "password";
  pass2Input.type = hidden ? "text" : "password";
  toggleBtn2.textContent = hidden ? "🙈" : "👁️";
};
