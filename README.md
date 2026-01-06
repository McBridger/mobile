# McBridger Mobile 🤖

[![Platform: Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![Framework: React Native](https://img.shields.io/badge/Framework-React_Native-blue.svg)](https://reactnative.dev)
[![Expo: SDK 52](https://img.shields.io/badge/Expo-SDK_52-black.svg)](https://expo.dev)

**The Android counterpart of the McBridger ecosystem. Fast, secure, and truly seamless.**

McBridger Mobile brings Apple-like continuity to the Android platform. By connecting to your Mac via BLE and using a high-performance JSI bridge, it enables cross-platform features that feel native to your device.

## ✨ Features
- **Deterministic Security:** 6-word mnemonic-based key derivation.
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