<div align="center">

# 💸 PayLink

### Instant UPI QR Generator — Privacy-First · 100% Offline · Open Source

[![License: MIT](https://img.shields.io/badge/License-MIT-4F46E5?style=for-the-badge&logo=opensourceinitiative&logoColor=white)](LICENSE)
[![Latest Release](https://img.shields.io/github/v/release/Ankush-das444/paylink?style=for-the-badge&logo=github&color=4F46E5&logoColor=white)](https://github.com/Ankush-das444/paylink/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android-4F46E5?style=for-the-badge&logo=android&logoColor=white)](https://github.com/Ankush-das444/paylink/releases)
[![Built with Compose](https://img.shields.io/badge/Jetpack_Compose-UI-818CF8?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![AndroidIDE](https://img.shields.io/badge/Built_on_Phone-AndroidIDE-10B981?style=for-the-badge&logo=android&logoColor=white)](https://androidide.com)

<br/>

> **Built entirely on an Android smartphone using [AndroidIDE](https://androidide.com) — no PC required.**  
> A production-grade, Jetpack Compose application compiled, tested, and shipped from a phone.

<br/>

<!-- SCREENSHOTS -->
<p align="center">
  <img src="https://i.ibb.co/bM58tTMC/Screenshot-20260426-220628.jpg" alt="Screenshot 20260426 220628" border="0" width="30%" alt="Main Screen"/>
  &nbsp;&nbsp;
  <img src="https://ibb.co/2Yc7Kqv4" width="30%" alt="Generated QR"/>
  &nbsp;&nbsp;
  <img src="https://ibb.co/6Jvxn01w" width="30%" alt="About Screen 1"/>
  <img src="https://ibb.co/S4qRgzjh" width="30%" alt="About Screen 2"/>
  <img src="https://ibb.co/TB6sC3z1" width="30%" alt="History"/>
</p>
</div>

---

## 🚀 What is PayLink?

PayLink is a **privacy-first, fully offline** utility for Android that lets anyone generate a standard UPI payment URI and a custom-styled QR code in seconds. No accounts. No servers. No tracking. Just open it, enter your UPI ID, set an amount, and share.

Whether you're a street vendor, a freelancer, or just splitting a bill — PayLink makes requesting payments as fast as unlocking your phone.

---

## 📥 Downloads

<div align="center">

| Platform | Status |
|---|---|
| 🤖 **GitHub Releases** | [![Download](https://img.shields.io/github/v/release/Ankush-das444/paylink?label=Download&style=flat-square&color=4F46E5)](https://github.com/Ankush-das444/paylink/releases/latest) |
| 📦 **F-Droid** | *Submission in progress* |
| 🌐 **Orion Store** | *Coming soon* |

</div>

> **Sideloading:** Download the latest `.apk` from [Releases](https://github.com/Ankush-das444/paylink/releases/latest) and install directly. Enable *"Install from unknown sources"* in your Android settings if prompted.

---

## ✨ Features

### 🔒 Privacy & Security
- **Zero network dependency** — no data ever leaves your device during normal use
- **No accounts, no analytics, no ads** — ever
- QR matrix computation and payment history are entirely on-device
- Update checker pings the GitHub API only when *you* tap the button — never automatically

### ⚡ Instant QR Generation
- Generates standard `upi://pay` URIs compliant with all major UPI apps (GPay, PhonePe, Paytm, BHIM, etc.)
- High error-correction Level H QR codes with a custom **indigo palette** and centered brand logo
- Quick-preset amount chips (₹10, ₹50, ₹100, ₹500…) for zero-keyboard speed

### 📤 Smart Sharing
- Securely exports the generated QR bitmap via Android `FileProvider` — no temp file leaks
- One-tap share directly to **WhatsApp** or any installed app via the native Android share sheet
- Optionally include the raw UPI deep-link alongside the image

### 📋 Local Payment History
- Saves every generated request to a local **SQLite** database (no ORM — native `SQLiteOpenHelper`)
- Browse and re-fill past transactions instantly from the history sheet
- Keeps the last 50 entries, sorted by recency

### 🎨 Premium UI
- Built top-to-bottom with **Jetpack Compose**
- Animated ambient background gradients that drift softly across the screen
- Shimmer border effect on the QR result card
- Polished About screen with developer info, support link, and inline update checker

### 🔄 Lightweight Update Checker
- Taps the **GitHub Releases API** on demand to compare `tag_name` against the current build constant
- Fully **F-Droid compliant** — no proprietary update frameworks, no Firebase, no Play Services
- Shows a green download CTA with the exact new version tag if an update is found
- Gracefully handles timeouts and network errors with a retry prompt

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| QR Engine | ZXing Core |
| Database | Native SQLite (`SQLiteOpenHelper`) |
| Async | Kotlin Coroutines + `StateFlow` |
| Image Sharing | `FileProvider` |
| Update Checking | GitHub REST API v3 (tag-based) |
| Build Environment | AndroidIDE (on-device) |

---

## 🏗️ Building Locally

PayLink is optimized to compile in [AndroidIDE](https://androidide.com) directly on your Android device, but it works equally well in Android Studio on a desktop.

### Prerequisites
- Android SDK 26+
- Kotlin 1.9+
- Gradle 8+

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/Ankush-das444/paylink.git
cd paylink

# 2. Open in AndroidIDE or Android Studio

# 3. Sync Gradle and resolve dependencies

# 4. Build the debug APK
./gradlew assembleRelease

# 5. Install on a connected device
./gradlew installRelease
```

### Dependencies (`build.gradle`)

```groovy
dependencies {
    implementation "androidx.activity:activity-compose:1.8.2"
    implementation "androidx.compose.material3:material3:1.2.1"
    implementation "androidx.compose.material:material-icons-extended:1.6.4"
    implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0"
    implementation "com.google.zxing:core:3.5.3"
}
```

> **Note:** PayLink intentionally avoids Room, Hilt, and other annotation-processor-heavy libraries to maintain compatibility with AndroidIDE's on-device compilation pipeline.

---

## 🔄 Update Checker — How It Works

The update checker is deliberately minimal and transparent:

1. User taps **"Check for Updates"** on the About screen
2. App makes a single `GET` request to `https://api.github.com/repos/Ankush-das444/paylink/releases/latest`
3. Parses the `tag_name` field from the JSON response
4. Compares against the `CURRENT_VERSION` constant baked into the build
5. If they differ → shows a green **"Download vX.Y.Z"** button pointing to the GitHub Releases page
6. If they match → shows **"You're up to date!"**
7. On any network failure → shows **"Couldn't check — tap to retry"**

No telemetry. No background jobs. No scheduled checks. The internet is only touched when the user explicitly asks.

---

## 🤝 Contributing

Contributions are warmly welcomed! Here's how to get started:

1. **Fork** the repository
2. **Create** a feature branch: `git checkout -b feature/your-feature-name`
3. **Commit** your changes: `git commit -m 'Add: your feature description'`
4. **Push** to the branch: `git push origin feature/your-feature-name`
5. **Open** a Pull Request with a clear description

### Good First Issues
- Add UI screenshots to `docs/screenshots/`
- Add more quick-preset amount chips
- Localize strings for regional languages
- F-Droid metadata (`fastlane/` structure)

---

## 👤 Credits

Developed with ❤️ by **Ankush Das**

- GitHub: [@ankushp](https://github.com/Ankush-das444/)
- Built 100% on Android using [AndroidIDE](https://androidide.com)

---

## 📄 License

```
MIT License

Copyright (c) 2026 Ankush Das

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
```

---

<div align="center">

**If PayLink saved you time, consider starring ⭐ the repo — it helps more people find it.**

[![Star on GitHub](https://img.shields.io/github/stars/ankushp/paylink?style=social)](https://github.com/Ankush-das444/paylink)

</div>
