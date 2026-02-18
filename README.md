# VitaMed – Smart Pharmaceutical Ordering & Communication Platform

## 📌 Overview

**VitaMed** is a full-stack healthcare ordering platform designed to digitize the communication and ordering workflow between pharmacies and pharmaceutical companies.

The system eliminates manual phone orders and paperwork by providing a real-time synchronized platform consisting of:

* Android Mobile Application (Pharmacy Client)
* Responsive Web Portal (Admin & Company Dashboard)
* Firebase Backend (Authentication, Firestore Database, Storage)

This project was developed as a senior graduation project in Computer Science.

---

## 🎯 Problem

Traditional pharmaceutical ordering relies on phone calls, paper invoices, and delayed confirmations.
This leads to:

* Human errors in orders
* Missing invoices
* No order tracking
* Communication delays
* Stock mismanagement

---

## 💡 Solution

VitaMed provides a centralized digital platform that allows pharmacies to:

* Browse products in real time
* Place orders instantly
* Track invoices and order status
* Communicate directly with companies
* Receive stock updates automatically

---

## 🧱 System Architecture

```
Client (Android App)  ---> Firebase Authentication
                        ---> Firestore Database
                        ---> Cloud Storage

Admin (Web Portal)  ---> Manage Products
                       ---> Manage Orders
                       ---> Manage Users
                       ---> Generate Invoices
```

The mobile and web applications are synchronized in real-time using Firestore listeners.

---

## 📱 Mobile Application Features (Pharmacy)

* Secure Login & Email Verification
* Product Catalog Browsing
* Add to Cart & Place Orders
* Order History Tracking
* Invoice Viewing
* Return Requests
* Real-time Chat with Company
* Notifications for stock updates

---

## 🖥️ Web Portal Features (Admin / Company)

* Dashboard with statistics
* Product Management (CRUD)
* User Approval / Blocking
* Order Processing
* Invoice Generation
* Returns Management
* Feedback Monitoring
* Live Chat with pharmacies

---

## 🔐 Security

* Firebase Authentication
* Role-based access control (Admin / Pharmacy)
* Firestore security rules
* Email verification required before access
* Protected admin routes

---

## 🛠️ Technologies Used

### Mobile

* Java (Android Studio)
* XML UI Design
* Firebase SDK

### Web

* HTML
* CSS
* JavaScript

### Backend

* Firebase Authentication
* Cloud Firestore
* Firebase Storage

---

## 📊 Key Concepts Implemented

* Real-time database synchronization
* Client-Server architecture
* Role-based authorization
* Cloud backend integration
* REST-like structured operations
* State management through listeners

---

## 📁 Project Structure

```
VitaMed
│
├── mobile-app        → Android application
├── web-portal        → Admin dashboard
└── README.md
```

---

## 🚀 Future Improvements

* Payment gateway integration
* Delivery tracking system
* Push notifications
* Multi-company support
* Analytics dashboard with charts

---

## 👨‍💻 Author

**Mohamad Imad Ghanem**
Computer Science Graduate

---

## 📄 License

This project is for educational and demonstration purposes.
