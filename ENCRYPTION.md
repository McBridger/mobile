# McBridge Mobile Encryption Protocol

## Status: Verified & Synchronized (Core Cryptography) - 2025-12-23

### 1. Key Derivation (PBKDF2)
- **Algorithm:** PBKDF2-HMAC-SHA256.
- **Iterations:** 600,000 (OWASP compliant).
- **Salt:** 256-bit hex string, stored in `Shared.xcconfig` (Mac) and provided via `EncryptionService.setup` (Android).
- **Output:** 256-bit Master Key.

### 2. Domain Separation (HKDF)
- Derived keys for specific tasks using **HKDF (SHA256)** with the Master Key.
- **Protocol Match:** Matches Apple `CryptoKit` HKDF implementation.
- **Mechanism:** `Extract-and-Expand` (RFC 5869).
    - **Extract:** Uses a salt of 32 zero-bytes to derive a Pseudo-Random Key (PRK).
    - **Expand:** Uses context-specific `info` strings to derive final keys/UUIDs.
- **Derived Identifiers:**
    - `McBridge_Advertise_UUID` -> Used for BLE Service Discovery filtering.
    - `McBridge_Service_UUID` -> Primary BLE Service UUID.
    - `McBridge_Characteristic_UUID` -> Main data transfer characteristic (Read/Write/Notify).
- **Derived Keys:**
    - `McBridge_Encryption_Domain` (32 bytes) -> Used as the `SymmetricKey` for AES-GCM payload encryption.

### 3. Payload Security (AES-GCM)
- **Cipher:** AES/GCM/NoPadding.
- **Format:** `Nonce (12B) + Ciphertext + Tag (16B)`.
- **Compatibility:** Exact match with iOS `CryptoKit` (SealedBox combined format).
- **Integrity:** All messages are authenticated via GCM Tag. Invalid or tampered messages are silently discarded.

### 4. Replay Protection
- `Message` model includes a `ts` (timestamp) field in the encrypted JSON.
- Receivers discard any message where `abs(now - ts) > 60s`.

### 5. Implementation Verification
- Verified cross-platform compatibility between **Android (JCA/javax.crypto)** and **macOS/iOS (CryptoKit/CommonCrypto)**.
- UUIDs derived from the same passphrase and salt match exactly on both platforms.
- Encrypted payloads (Type 0: Clipboard, Type 1: Device Name) are successfully decrypted across platforms.

## Next Steps
1. **Mnemonic Implementation:** Replace hardcoded testing passphrase with a 6-word mnemonic generation/entry system.
2. **Secure Storage:** Store the derived Master Key or Passphrase in `EncryptedSharedPreferences` (Android) and `Keychain` (macOS).
3. **De-hardcoding & Cleanup:**
    - Remove all hardcoded UUIDs from source code and configuration files.
    - **Crucial:** Remove BLE UUIDs from GitHub Secrets and CI/CD pipelines. Since UUIDs are now deterministically derived from the Master Key, they no longer need to be stored as secrets.
4. **Automatic Handshake:** Fully automate the `startDiscovery` -> `connect` flow in the `Broker` using the verified `Advertise_UUID`.