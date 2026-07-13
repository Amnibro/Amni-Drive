# Checklist — Auto-start "plug in & go / wireless & go" (v1.4.0)

Goal (Anthony 2026-07-12): Amni-Drive must behave LIKE CarPlay/Android Auto on a mounted phone/tablet dash — **plug in and go, wireless and go**: auto-enter the drive dashboard the instant the car connects, no tapping.

- [x] Back up DriveConfig, ConfigStore, AmniDriveApp, SettingsActivity, AndroidManifest, build.gradle (backups/*.v1.3.17.bak)
- [x] DriveConfig: add autoStartOnPower(=true), autoStartOnBluetooth(=false), carBtMac(=""), autoEnterCarMode(=true)
- [x] ConfigStore: persist the 4 new fields (keys + setters + map)
- [x] auto/CarConnectionService: FGS; runtime receivers ACTION_POWER_CONNECTED/DISCONNECTED + BluetoothDevice.ACTION_ACL_CONNECTED/DISCONNECTED; on qualifying connect -> enableCarMode + startActivity(openNavIntent) + full-screen-intent notification fallback (BAL-safe)
- [x] auto/AutoStartReceiver: BOOT_COMPLETED -> start CarConnectionService (survives reboot)
- [x] AmniDriveApp: start CarConnectionService on first launch
- [x] Manifest: perms RECEIVE_BOOT_COMPLETED, USE_FULL_SCREEN_INTENT, WAKE_LOCK, SYSTEM_ALERT_WINDOW; register service + receiver; CAR_DOCK category on MainActivity
- [x] SettingsActivity: "Auto-start" pane — power/bluetooth/car-mode toggles + Learn-my-car bonded-BT picker
- [x] Bump version 1.3.17 -> 1.4.0 (versionCode 31)
- [x] Build :app:compileDebugKotlin + assembleDebug green
- [ ] ON-DEVICE (rule 8, needs Anthony parked/WiFi): plug USB -> dash auto-opens; connect car BT -> dash auto-opens; verify full-screen intent grant on Android 14+
- [x] Update architecture_map.md + changelog.md

## Notes / risk
- Android 12+ background-activity-launch: primary launch path is a **full-screen-intent** high-priority notification (same class as incoming-call), plus a direct startActivity attempt and SYSTEM_ALERT_WINDOW exemption. On Android 14+ USE_FULL_SCREEN_INTENT may need a one-time user grant (Settings shortcut provided).
- Power-connect fires on ANY charger; the Bluetooth-MAC match is what makes it car-specific for "wireless and go". On a dedicated tablet dash, power-connect alone is the CarPlay-like trigger.
