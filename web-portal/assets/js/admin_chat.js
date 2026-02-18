import { db } from "./firebase.js";
import { setupLogout } from "./logout.js";
import {
  collection,
  addDoc,
  query,
  where,
  onSnapshot,
  orderBy,
  getDoc,
  doc,
  serverTimestamp,
  writeBatch
} from "https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js";

const ADMIN_ID = "admin";

let currentRoomId = null;

// ✅ unsubscribe handlers
let unsubRooms = null;
let unsubMessages = null;

// ✅ unread listeners per room
let unreadUnsubs = new Map();

// ✅ room cache for active highlight
let activeRoomElement = null;

/* ================= HELPERS ================= */

// ✅ نزّل لآخر الشات بشكل مضمون
function scrollToBottom(box) {
  requestAnimationFrame(() => {
    box.scrollTop = box.scrollHeight;
    requestAnimationFrame(() => {
      box.scrollTop = box.scrollHeight;
    });
  });
}

/* ================= LOAD ROOMS (REAL TIME) ================= */
function loadRooms() {
  const roomsBox = document.getElementById("rooms");

  // stop old rooms listener
  if (unsubRooms) unsubRooms();

  const qRooms = query(
    collection(db, "chat_rooms"),
    where("admin_id", "==", ADMIN_ID)
  );

  unsubRooms = onSnapshot(qRooms, async (snap) => {

    // ✅ stop old unread listeners
    unreadUnsubs.forEach(u => u && u());
    unreadUnsubs.clear();

    roomsBox.innerHTML = "";

    if (snap.empty) {
      roomsBox.innerHTML = `<div style="padding:12px;color:#64748b;">No rooms found.</div>`;
      return;
    }

    for (const d of snap.docs) {
      const roomId = d.id;
      const room = d.data();

      // ✅ user name
      let name = "Unknown Pharmacy";
      if (room.user_id) {
        const uSnap = await getDoc(doc(db, "users", room.user_id));
        if (uSnap.exists()) {
          const u = uSnap.data();
          name = u.pharmacyname || u.username || "Unknown Pharmacy";
        }
      }

      const div = document.createElement("div");
      div.className = "room";

      const nameDiv = document.createElement("div");
      nameDiv.className = "room-name";
      nameDiv.textContent = name;

      // ✅ unread badge
      const badge = document.createElement("span");
      badge.className = "unread-badge";
      badge.style.display = "none";
      nameDiv.appendChild(badge);

      div.appendChild(nameDiv);

      const sub = document.createElement("small");
      sub.textContent = "Active chat";
      div.appendChild(sub);

      div.onclick = () => openRoom(roomId, name, div);

      roomsBox.appendChild(div);

      // ✅ attach unread listener
      const unsubUnread = listenUnread(roomId, badge);
      unreadUnsubs.set(roomId, unsubUnread);

      // ✅ keep room highlighted if open
      if (currentRoomId === roomId) {
        div.classList.add("active");
        activeRoomElement = div;
      }
    }
  });
}

/* ================= UNREAD BADGE (REAL TIME) ================= */
function listenUnread(roomId, badge) {
  const qUnread = query(
    collection(db, "chat_messages"),
    where("room_id", "==", roomId),
    where("sender_role", "==", "user"),
    where("seen", "==", false)
  );

  return onSnapshot(qUnread, (snap) => {
    if (snap.empty) {
      badge.style.display = "none";
      badge.textContent = "";
    } else {
      badge.style.display = "flex";
      badge.textContent = snap.size;
    }
  });
}

/* ================= OPEN ROOM (REAL TIME) ================= */
function openRoom(roomId, name, roomElement) {

  // ✅ إذا نفس الغرفة مفتوحة
  if (currentRoomId === roomId) return;

  currentRoomId = roomId;

  // ✅ active room UI
  if (activeRoomElement) activeRoomElement.classList.remove("active");
  if (roomElement) {
    roomElement.classList.add("active");
    activeRoomElement = roomElement;
  }

  const header = document.getElementById("chatHeader");
  const box = document.getElementById("messages");

  header.textContent = name;

  // ✅ stop old messages listener
  if (unsubMessages) unsubMessages();

  // ✅ clear old messages
  box.innerHTML = "";

  // ✅ always go down when opening room
  scrollToBottom(box);

  const qMsgs = query(
    collection(db, "chat_messages"),
    where("room_id", "==", roomId),
    orderBy("created_at", "asc")
  );

  unsubMessages = onSnapshot(qMsgs, async (snap) => {

    // ✅ إعادة رسم كامل (مضمون) + auto scroll دايمًا
    box.innerHTML = "";

    // ✅ mark unseen user messages (batch)
    const unseenRefs = [];

    snap.forEach(docu => {
      const m = docu.data() || {};

      if (m.sender_role === "user" && m.seen === false) {
        unseenRefs.push(docu.ref);
      }

      const div = document.createElement("div");
      div.className = "msg " + (m.sender_role || "user");
      div.textContent = m.text || "";
      box.appendChild(div);
    });

    // ✅ auto scroll ALWAYS (حسب طلبك)
    scrollToBottom(box);

    // ✅ batch update seen
    if (unseenRefs.length > 0) {
      const batch = writeBatch(db);
      unseenRefs.forEach(ref => batch.update(ref, { seen: true }));
      await batch.commit();
    }
  });
}

/* ================= SEND MESSAGE ================= */
document.getElementById("sendBtn").onclick = async () => {
  const input = document.getElementById("msgInput");
  const btn = document.getElementById("sendBtn");
  const text = input.value.trim();

  if (!text) return;
  if (!currentRoomId) return alert("Select a chat room first");

  btn.disabled = true;

  try {
    await addDoc(collection(db, "chat_messages"), {
      room_id: currentRoomId,
      sender_id: ADMIN_ID,
      sender_role: "admin",
      text,
      seen: false,
      created_at: serverTimestamp()
    });

    input.value = "";

    // ✅ always scroll down after sending
    const box = document.getElementById("messages");
    scrollToBottom(box);

  } finally {
    btn.disabled = false;
  }
};

/* ================= INIT ================= */
loadRooms();

/* ================= CLEANUP ================= */
window.addEventListener("beforeunload", () => {
  if (unsubRooms) unsubRooms();
  if (unsubMessages) unsubMessages();
  unreadUnsubs.forEach(u => u && u());
  unreadUnsubs.clear();
});
setupLogout();