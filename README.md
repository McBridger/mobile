# McBridge Mobile 🤖

[![Platform: Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![Framework: React Native](https://img.shields.io/badge/Framework-React_Native-blue.svg)](https://reactnative.dev)
[![Expo: SDK 52](https://img.shields.io/badge/Expo-SDK_52-black.svg)](https://expo.dev)

**The Android counterpart of the McBridge ecosystem. Secure, offline-first, and lightning-fast.**

McBridge Mobile connects to your Mac via Bluetooth Low Energy to synchronize your clipboard. Using a JSI-powered native bridge, it ensures low latency and high security for every piece of data you copy.

## ✨ Features
- **Deterministic Security:** Your keys are derived from a 6-word mnemonic.
- **Background Service:** Syncs your clipboard even when the app is in the background (requires permissions).
- **AES-GCM Encryption:** Fully encrypted data transfer over BLE.
- **Native Performance:** Uses Kotlin for the BLE stack and JSI for high-speed communication with React Native.

## 🛠 Tech Stack
- **React Native (Expo):** For the UI and core logic.
- **Zustand:** Lightweight state management.
- **Kotlin:** Custom native modules for the Bluetooth Low Energy (BLE) stack.
- **JSI (JavaScript Interface):** For high-performance communication between JS and Native layers.

## 🚀 Development

1. **Install dependencies:**
   ```bash
   bun install # or npm install
   ```

2. **Start the development server:**
   ```bash
   npx expo start
   ```

3. **Run on Android:**
   ```bash
   npx expo run:android
   ```

> **Note:** Since this app uses custom native modules for BLE, it requires a Development Build. Standard Expo Go will not work.

---
[Encryption Details](ENCRYPTION.md) • [Organization Profile](https://github.com/McBridger)