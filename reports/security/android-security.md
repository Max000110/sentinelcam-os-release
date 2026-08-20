# Android Application Security Audit

## 1. Manifest & Component Analysis
- **Package ID**: `com.sentinelcam.node`
- **Target API**: 35 (Android 15) | **Min API**: 26 (Android 8.0)
- **Backup Configuration**: `android:allowBackup="false"` (Prevents ADB backup extraction of cryptographic keystore material).
- **Exported Components**:
  - `MainActivity`: `exported="true"` with `android.intent.action.MAIN` / `LAUNCHER`.
  - `CctvForegroundService`: `exported="false"` (Strictly inaccessible by third-party apps).
  - `BootReceiver`: `exported="true"` but protected with `permission="android.permission.RECEIVE_BOOT_COMPLETED"`.

## 2. Permission Classification & Least-Privilege
| Permission | Classification | Purpose / Verification |
| :--- | :--- | :--- |
| `android.permission.CAMERA` | **REQUIRED** | Real-time video frame capture via CameraX. |
| `android.permission.RECORD_AUDIO` | **REQUIRED** | Two-way push-to-talk WebRTC audio. |
| `android.permission.FOREGROUND_SERVICE` | **REQUIRED** | Continuous 24x7 CCTV operation. |
| `android.permission.FOREGROUND_SERVICE_CAMERA` | **REQUIRED** | Android 14+ FGS Camera type compliance. |
| `android.permission.FOREGROUND_SERVICE_MICROPHONE` | **REQUIRED** | Android 14+ FGS Microphone type compliance. |
| `android.permission.FOREGROUND_SERVICE_DATA_SYNC` | **REQUIRED** | Android 14+ FGS Telemetry & Signaling sync. |
| `android.permission.WAKE_LOCK` | **REQUIRED** | Prevent CPU sleep during screen-off operation. |
| `android.permission.RECEIVE_BOOT_COMPLETED` | **REQUIRED** | Auto-restart streaming after power outage / reboot. |
| `android.permission.ACCESS_FINE_LOCATION` | **EXCLUDED** | Audited and verified zero location permissions requested. |

## 3. Storage & Cryptography
- **EncryptedSharedPreferences**: Uses Android Keystore hardware-backed master key (`MasterKeys.AES256_GCM_SPEC`) with `AES256_SIV` key encryption and `AES256_GCM` value encryption.
- **Debounced Writes**: UI updates debounce storage writes by 500ms, eliminating main-thread flash wear and UI lockups.
- **R8 / ProGuard Optimization**: Configured with `isMinifyEnabled = true`, `isShrinkResources = true`, and dedicated keep rules for WebRTC, Gson, OkHttp, and CameraX.
