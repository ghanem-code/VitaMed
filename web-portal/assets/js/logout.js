import { auth } from "./firebase.js";
import { signOut } from "https://www.gstatic.com/firebasejs/11.0.1/firebase-auth.js";

export function setupLogout() {
  const btn = document.getElementById("logoutSidebar");
  if (!btn) return;

  btn.onclick = async () => {
    try {
      localStorage.clear();
      await signOut(auth);
    } catch (e) {
      console.log("Logout error:", e.message);
    } finally {
      window.location.href = "../login.html";
    }
  };
}
