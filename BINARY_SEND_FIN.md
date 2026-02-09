# Roadmap: Finalizing Unified Binary Protocol

## 1. Streaming Encryption (AES-CTR/GCM)

- **Goal**: Ensure P2P privacy in local networks.
- **Technical**: Use AES-GCM for authenticated encryption. Implement a wrapper for `BufferedSource` and `BufferedSink` to encrypt/decrypt chunks on the fly.
- **Key Management**: Derive session keys using HKDF from the 12-word mnemonic + salt.

## 2. Dynamic Port Negotiation

- **Goal**: Avoid collisions on port 49152.
- **Logic**: Let `ServerSocket(0)` pick a free port. Update `TcpTransport` to retrieve this port and pass it to `Broker`. The port must be included in the `IntroMessage` sent via BLE.

## 3. Large Text Handling

- **Current**: `TinyMessage` is used for all text (clipboard).
- **Problem**: Very large text (e.g., logs, articles) can choke the signalling channel (BLE).
- **Fix**: If text size > 32KB, automatically switch to `BlobType.TEXT` and send it via TCP as a stream.

## 4. Intelligent Cache Management

- **Strategy**: Files in `mcbridger_blobs` are redundant after copying to `Downloads/McBridger`.
- **Constraint**: `FileProvider` needs the cached file for the "Paste" operation from Clipboard to work in some apps.
- **Solution**: Implement a "Clear Cache" button in settings + auto-cleanup of files older than 24h or on every app cold start.

## 5. UI Progress & State Synchronization

- **Problem**: `BlobMessage` is currently a static event. UI doesn't know if the stream is still running.
- **Solution**:
  - **JS-Side**: Enhance `Blob` type with an optional `transferState` object (progress, speed, status).
  - **Native-Side**: Emit lifecycle events: `onTransferStarted(id)`, `onTransferProgress(id, current, total, speed)`, `onTransferFinished(id, success)`.
  - **Zustand**: Maintain an `activeTransfers` map. Card components should "subscribe" to this map by message ID to show live progress bars.
  - **Persistence**: Ensure only the final "Success" or "Error" state is eventually persisted to the JSON history, avoiding disk thrashing during the transfer.

## 6. Throughput Optimization (The 100Mbps Goal)

- **Current Bottleneck**: Likely repeated File IO or small buffer sizes.
- **Fixes**:
  - Increase TCP frame size to 1MB.
  - Use a reusable byte-buffer pool to reduce GC pressure.
  - Ensure `FileHandle` stays open during the entire stream (avoid per-chunk open/close).

## 7. Native Integration Tests (Kotlin)

- **Goal**: Stop relying on flaky Maestro for protocol verification.
- **Implementation**: Create a test suite that spawns real local TCP sockets. Use `MockK` to verify `Broker` behavior. Simulate binary frames directly in Kotlin to test edge cases (disconnections, corrupted frames).

## 8. Outgoing Progress Tracking

- **Goal**: Symmetry between receiving and sending.
- **Fix**: Update `TcpTransport.sendBlob` to report progress back to `Broker`. Trigger `NotificationService` and JS events for outgoing files so the user knows "how much is left" on the sender side.

## 9. Live Notifications (Android 16+ Progress-Centric API)

- **Goal**: Achieve "Dynamic Island" style visibility for ongoing transfers.
- **Target**: Status bar chips, enhanced Always-On Display, and Lockscreen persistence.
- **Technical**: Once Android 16 is supported, refactor `NotificationService.showProgress` to use `Notification.ProgressStyle`. This will replace the standard progress bar with a system-integrated lifecycle tracker.
