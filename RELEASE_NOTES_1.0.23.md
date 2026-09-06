# FocusLock 1.0.23 — Timer save compatibility

- Fixed three unsafe Android haptic constant reads in Apply time, Save & start, and zero-duration feedback. These were emitted as DEX `sget` instructions for fields introduced in Android API 30, so Android 8–10 could throw `NoSuchFieldError`. The app now uses the supported keyboard-tap feedback value.
- Saving no longer triggers the notification permission dialog. Notification setup remains available through the existing permission flow.
- Monitor startup reuses the existing permission-aware, health-aware starter and handles runtime startup/scheduling errors without throwing them out of the save callback.
- App version is consistently 1.0.23 (123), including the account screen and diagnostics metadata.
- Signed using the same production certificate as v1.0.22, with APK Signature Scheme v2. Install over the existing app.

The haptic failure is a verified compatibility defect in the shipped v1.0.22 code. Returning to Chrome can be the visible result of an app crash. The user's device model, OS version, and crash log were not available, so this is not a device-confirmed diagnosis. The earlier update-dialog explanation did not establish the cause of this remaining issue.

## Validation

Android D8 processing passed with min API 26. Compiled-code checks confirm removal of all three newer haptic field reads, preservation of timer persistence and feedback, and the guarded monitor-start path. Resource contents, package identity, certificate, ZIP integrity, DEX checksums, and APK v2 signature were checked. A modified APK fails signature verification.

No connected Android device or emulator was available. On-device acceptance: apply both timer wheels, save the plan, confirm FocusLock stays open, then open a selected app and check that its use limit and block countdown still work.

## Rebuilding

The Java source reflects the changes. The release was produced from the existing v1.0.22 APK using the instruction-aware `tools/TimerSaveHotfix.java`, Android D8, and the packaging/signing scripts under `tools/`. This preserves unrelated compiled code and resources. The scripts require the previous APK, Java 17, Android command-line tools (dexlib2 and D8), Python with cryptography, and the original signing key. Private signing material is excluded from this archive.
