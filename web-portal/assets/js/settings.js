import { db, auth } from "./firebase.js";
import { setupLogout } from "./logout.js";

import {
  doc,
  onSnapshot,
  updateDoc,
  collection,
  addDoc,
  serverTimestamp,
  deleteDoc,
  getDocs,
  query,
  where
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

import {
  EmailAuthProvider,
  reauthenticateWithCredential,
  updatePassword,
  deleteUser
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-auth.js";

let selectedRating = 0;
let unsubUserProfile = null; // ✅ listener holder

/* ================= LOAD PROFILE (REAL-TIME) ================= */
auth.onAuthStateChanged((user) => {
  if (!user) return;

  // ✅ stop old listener if exists
  if (unsubUserProfile) unsubUserProfile();

  const userRef = doc(db, "users", user.uid);

  unsubUserProfile = onSnapshot(userRef, (snap) => {
    if (!snap.exists()) return;

    const d = snap.data();

    username_input.value = d.username || "";
    phone_input.value = d.phone || "";
    pharmacy_input.value = d.pharmacyname || "";
    location_input.value = d.location || "";
    email_input.value = d.email || "";
  });
});

/* ================= SAVE PROFILE ================= */
window.saveProfile = async () => {
  await updateDoc(doc(db, "users", auth.currentUser.uid), {
    username: username_input.value.trim(),
    phone: phone_input.value.trim(),
    pharmacyname: pharmacy_input.value.trim(),
    location: location_input.value.trim()
  });

  alert("Profile updated ✅");
};

/* ================= CHANGE PASSWORD ================= */
window.changePassword = async () => {
  if (new_pass.value !== confirm_pass.value)
    return alert("Passwords do not match");

  const cred = EmailAuthProvider.credential(
    auth.currentUser.email,
    old_pass.value
  );

  await reauthenticateWithCredential(auth.currentUser, cred);
  await updatePassword(auth.currentUser, new_pass.value);

  alert("Password updated ✅");
};

/* ================= ⭐ STAR RATING ================= */
document.querySelectorAll("#starRating span").forEach(star => {
  star.onclick = () => {
    selectedRating = Number(star.dataset.value);

    document.querySelectorAll("#starRating span").forEach(s => {
      s.classList.toggle(
        "active",
        Number(s.dataset.value) <= selectedRating
      );
    });
  };
});

/* ================= SEND FEEDBACK ================= */
window.sendFeedback = async () => {
  if (selectedRating === 0)
    return alert("Please select rating");

  await addDoc(collection(db, "feedback"), {
    user_id: auth.currentUser.uid,
    pharmacyname: pharmacy_input.value,
    location: location_input.value,
    message: feedback_message.value,
    rating: selectedRating,
    created_at: serverTimestamp()
  });

  feedback_message.value = "";
  selectedRating = 0;

  document
    .querySelectorAll("#starRating span")
    .forEach(s => s.classList.remove("active"));

  alert("Feedback sent ✅");
};

/* ================= DELETE ACCOUNT (FULL CASCADE DELETE) ================= */
window.deleteAccount = async () => {

  const confirmDelete = confirm(
    "⚠️ This will permanently delete your account and all your records.\nAre you sure?"
  );

  if (!confirmDelete) return;

  const user = auth.currentUser;
  const uid = user.uid;

  try {

    // ✅ stop profile listener
    if (unsubUserProfile) unsubUserProfile();

    /* ================== 1) DELETE USER FEEDBACK ================== */
    const fbSnap = await getDocs(
      query(collection(db, "feedback"), where("user_id", "==", uid))
    );

    for (const d of fbSnap.docs) {
      await deleteDoc(d.ref);
    }

    /* ================== 2) DELETE USER INVOICES + THEIR ORDER_ITEMS ================== */
    const invSnap = await getDocs(
      query(collection(db, "invoices"), where("user_id", "==", uid))
    );

    for (const invDoc of invSnap.docs) {
      const invData = invDoc.data();

      // ✅ delete order_items linked to this invoice order_id
      if (invData.order_id) {
        const itemsSnap = await getDocs(
          query(collection(db, "order_items"), where("order_id", "==", invData.order_id))
        );

        for (const item of itemsSnap.docs) {
          await deleteDoc(item.ref);
        }
      }

      // ✅ delete invoice itself
      await deleteDoc(invDoc.ref);
    }

    /* ================== 3) DELETE FIRESTORE USER DOC ================== */
    await deleteDoc(doc(db, "users", uid));

    /* ================== 4) DELETE AUTH USER ================== */
    await deleteUser(user);

    alert("Account deleted successfully ✅");
    window.location.href = "../login.html";

  } catch (e) {
    console.log(e);
    alert("Re-login required before deleting account ❗");
  }
};
setupLogout();

