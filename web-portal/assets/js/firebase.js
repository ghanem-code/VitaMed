// firebase.js
import { initializeApp } from "https://www.gstatic.com/firebasejs/11.0.1/firebase-app.js";
import { getFirestore } from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";
import { getAuth } from "https://www.gstatic.com/firebasejs/11.0.1/firebase-auth.js";

const firebaseConfig = {
    apiKey: "AIzaSyDoc3-36VNDo1B_UFMr3oD5uNgC4vT9nQY",
    authDomain: "vitamed-9cd0d.firebaseapp.com",
    projectId: "vitamed-9cd0d",
    storageBucket: "vitamed-9cd0d.firebasestorage.app",
    messagingSenderId: "589011864181",
    appId: "1:589011864181:web:2555f99de3db129104063c",
    measurementId: "G-RGZXFETNWX"
};

const app = initializeApp(firebaseConfig);

export const auth = getAuth(app);
export const db   = getFirestore(app);
