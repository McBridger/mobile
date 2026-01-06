# McBridger Encryption Protocol

## 🛡 Status: Verified & Operational (Core Cryptography)
**Last Audit:** 2026-01-05
**Compliance:** OWASP PBKDF2 Recommendations / RFC 5869 (HKDF)

### 1. Key Derivation (PBKDF2)
- **Entropy Source:** 6-word mnemonic phrase (user-provided).
- **Algorithm:** PBKDF2-HMAC-SHA256.
- **Iterations:** 600,000 (OWASP compliant).
- **Salt:** 256-bit unique hex string, injected via environment/config (stored in `Shared.xcconfig` (Mac) and provided via `EncryptionService.setup` (Android)).
- **Output:** 256-bit Master Key.

### 2. Domain Separation (HKDF)
We use **HKDF (SHA256)** to derive specific identifiers and keys from the Master Key. This ensures that even if one identifier is exposed, the Master Key and other domains remain secure.
- **Protocol:** Matches Apple `CryptoKit` HKDF implementation.
- **Mechanism:** `Extract-and-Expand` (RFC 5869).
    - **Extract:** Uses a 32-byte zero-salt to derive a Pseudo-Random Key (PRK).
    - **Expand:** Uses context-specific `info` strings to derive final keys/UUIDs.
- **Derived Identifiers (Deterministic):**
    - `McBridger_Advertise_UUID` -> Used for BLE Service Discovery.
    - `McBridger_Service_UUID` -> Primary BLE Service.
    - `McBridger_Characteristic_UUID` -> Main data transfer characteristic (Read/Write/Notify).
- **Derived Keys:**
    - `McBridge_Encryption_Domain` -> Used as the `SymmetricKey` for AES-GCM payload encryption.

### 3. Payload Security (AES-GCM)
- **Cipher:** AES/GCM/NoPadding.
- **Format:** `Nonce (12B) + Ciphertext + Tag (16B)`.
- **Integrity:** Every message is authenticated via GCM Tag. Invalid, tampered, or corrupted messages are discarded before processing.
- **Cross-Platform:** Byte-perfect compatibility between Android (JCA) and macOS (CryptoKit).

### 4. Replay & Temporal Protection
- **Timestamping:** Every encrypted payload contains a `ts` (timestamp) field.
- **TTL (Time-to-Live):** Messages older than 60 seconds are automatically rejected.

### 5. Secure Storage
- **Android:** Sensitive material is stored using `SharedPreferences` (transitioning to `EncryptedSharedPreferences`).
- **macOS:** Credentials are saved in the system `Keychain`.

---

## ✅ Implementation Progress

- [x] **Mnemonic Integration:** 6-word mnemonic system fully operational.
- [x] **Deterministic UUIDs:** All BLE identifiers are derived from the mnemonic.
- [x] **Cross-Platform Handshake:** Verified sync between Mac and Android clients.
- [x] **Replay Protection:** Timestamp-based verification implemented.
- [ ] **Hardware Security:** Migration to Biometric-backed KeyStore/Secure Enclave.
- [ ] **Perfect Forward Secrecy:** Ratchet-based key rotation for long-lived sessions.

---
[Organization Profile](https://github.com/McBridger)
