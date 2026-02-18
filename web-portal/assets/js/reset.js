import { auth } from "./firebase.js";
import { sendPasswordResetEmail } from "https://www.gstatic.com/firebasejs/11.0.1/firebase-auth.js";

window.sendReset = async function (event) {
    event.preventDefault();

    const email = document.getElementById("resetEmail").value.trim();

    if (!email) {
        alert("Enter your email");
        return;
    }

    try {
        await sendPasswordResetEmail(auth, email);
        alert("📩 Reset link sent! Check your inbox.");
        window.location.href = "login.html";
    } catch (err) {
        alert("Error: " + err.message);
    }
};
