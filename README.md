# Quick Transfer — Offline Android File Sharing Application

Quick Transfer is a modern, production-quality Android peer-to-peer file transfer application inspired by Google Quick Share. It allows users to transfer photos, videos, music, documents, APK files, and ZIP archives directly between nearby Android devices without requiring internet access, mobile data, accounts, or any external servers.

---

## 1. Networking Architecture & Implementation

Quick Transfer utilizes a layered local networking architecture:
- **Local Network Discovery (mDNS / DNS-SD via `android.net.nsd.NsdManager`)**:
  - The receiver advertises a `_quicktransfer._tcp.` service broadcast on the local subnet (Wi-Fi or Mobile Hotspot).
  - The sender actively scans for available peers and automatically resolves their device names, IP addresses, and ports.
- **Wi-Fi Direct (`WifiP2pManager`)**:
  - Peer-to-peer Wi-Fi discovery and direct connection establishment when no local router is present.
- **High-Throughput Socket Transfer Engine (`SocketTransferEngine`)**:
  - Direct TCP sockets with low memory overhead.
  - 64 KB chunked streaming directly between `ContentResolver.openInputStream()` and `Socket.getOutputStream()`.
  - Frame-based protocol: Handshake request/response, file headers, chunk streaming, file termination with SHA-256 integrity verification, and receiver file ACKs.
  - Supports live transfer speed calculation (KB/s, MB/s), estimated time remaining (ETA), pause, resume, and cancellation.
- **QR Code & Numeric PIN Pairing (`PairingSecurity` & CameraX)**:
  - Generates secure short-lived QR codes (`quicktransfer://pair?session=...&pin=...&ip=...&port=...&name=...`) and 6-digit verification PINs.
  - Sender can scan receiver's QR code using the built-in CameraX scanner or enter the PIN manually.
- **Security & Integrity**:
  - Strict path traversal prevention (`FileSecurityUtils.sanitizeFileName`).
  - Automatic collision resolution (`photo (1).jpg`).
  - Temporary files (`.qktmp`) used during active transfers; files are only moved to permanent storage after byte-size and SHA-256 checksums pass validation.

---

## 2. Testing Transfers Between Two Physical Android Phones

### Method A: Same Wi-Fi Network (Home / Office Wi-Fi)
1. Connect Phone A and Phone B to the same Wi-Fi network.
2. Open **Quick Transfer** on both phones.
3. On **Phone B (Receiver)**: Tap **Receive** on the Home dashboard. The screen will show "Ready to Receive", the device name, and a 6-digit PIN.
4. On **Phone A (Sender)**:
   - Tap **Send** and select files (photos, videos, documents, etc.).
   - Tap **Find Devices**.
   - Phone B will appear in the "Found Devices" list.
   - Tap Phone B to send.
5. On **Phone B**: An incoming transfer dialog will pop up showing the sender name, number of files, and total size. Tap **Accept**.
6. The transfer begins with real-time speed, progress bars, and ETA.

### Method B: Offline Field Transfer (No Wi-Fi Router / Mobile Hotspot)
1. On Phone A (or Phone B), turn ON **Personal Hotspot** (cellular data can be OFF).
2. Connect the other phone to this Hotspot.
3. Open **Quick Transfer** on both devices.
4. On Phone B (Receiver), tap **Receive** (or tap the **QR Code** icon).
5. On Phone A (Sender), select files, open the **QR Scanner** tab, and scan Phone B's screen.
6. The transfer connects instantly over the high-speed local hotspot link at full Wi-Fi speeds (20–80 MB/s).

---

## 3. Permissions & Privacy

Quick Transfer strictly adheres to the principle of least privilege:
- `android.permission.INTERNET` & `ACCESS_NETWORK_STATE`: Required for local TCP socket communication.
- `android.permission.ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`: Required for Wi-Fi Direct and local subnet detection.
- `android.permission.NEARBY_WIFI_DEVICES`: Required on Android 13+ (API 33+) for peer discovery without requiring location. Marked with `android:usesPermissionFlags="neverForLocation"`.
- `android.permission.BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`: Used on Android 12+ for nearby device discovery.
- `android.permission.ACCESS_FINE_LOCATION`: Limited to Android 12 and below (`maxSdkVersion="32"`), as mandated by legacy Android Wi-Fi Direct APIs.
- `android.permission.CAMERA`: Optional runtime permission; only requested when opening the QR code scanner.
- `android.permission.POST_NOTIFICATIONS`: Android 13+ runtime permission for background transfer notifications.
- `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_CONNECTED_DEVICE` / `DATA_SYNC`: Keeps transfers alive if the user switches apps.

**Privacy Guarantee**: Zero tracking, zero cloud servers, zero analytics. Files are never sent outside the local network.

---

## 4. Building Signed APK and Android App Bundle (.aab)

### Generating Signed Release APK
Run the following Gradle command in the root project directory:
```bash
gradle :app:assembleRelease
```
The output APK is generated at:
`app/build/outputs/apk/release/app-release.apk`

### Generating Android App Bundle (.aab) for Google Play
Run:
```bash
gradle :app:bundleRelease
```
The output AAB is generated at:
`app/build/outputs/bundle/release/app-release.aab`

---

## 5. Running Automated Unit Tests
To run local JVM unit tests and Robolectric tests:
```bash
gradle :app:testDebugUnitTest
```
All unit tests verify:
- Path traversal prevention & filename sanitization
- Checksum SHA-256 calculation
- PIN generation & QR payload encoding/decoding
- Frame protocol serialization and parsing
- Formatted file size, transfer speed, and ETA calculations
