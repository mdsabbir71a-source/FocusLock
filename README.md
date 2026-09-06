# FocusLock for Android

FocusLock lets a person choose distracting apps, set an allowed amount of actual foreground use, and receive a full-screen on-device pause when an app reaches that limit.

## What FocusLock does

- Counts time only while a user-selected app is in the foreground.
- Draws the pause screen directly above a locked app using Android's **Display over other apps** approval.
- Keeps the monitoring service visible as an Android foreground service while a boundary is active.
- Restarts an enabled boundary after the phone restarts or FocusLock is updated.
- Stores selected apps, timers, and progress locally on the device.

FocusLock does not include a VPN, content filter, adult-content browser, accessibility service, cloud analytics, or account system in this release.

## Android approvals

FocusLock asks for only the approvals needed for its core focus-limit feature:

1. **Usage Access** — to identify foreground time for apps the user selected. FocusLock does not read messages, typed text, or screen content.
2. **Display over other apps** — to show the lock screen above a selected app when its time is finished.
3. **Notifications** — optional but recommended so Android can display the active-boundary notification.

The user can disable any approval in Android Settings. Android manufacturers can still apply additional battery restrictions, so the app includes device-setup guidance rather than promising perfect behavior on every phone.

## Production release

The Play-ready release uses API level 36 and must be signed with the FocusLock production key. Copy `release-signing.properties.example` to `release-signing.properties` locally, create a secure keystore, and keep both passwords and the keystore in a password manager and offline backup. Never commit them.

For GitHub Actions, configure these repository secrets before producing the final Play upload:

- `FOCUSLOCK_KEYSTORE_BASE64`
- `FOCUSLOCK_KEYSTORE_PASSWORD`
- `FOCUSLOCK_KEY_ALIAS`
- `FOCUSLOCK_KEY_PASSWORD`

The workflow publishes a debug APK for internal testing on each push, and a signed release Android App Bundle only when manually dispatched with the signing secrets available.

## Before Google Play production

- Complete internal and closed testing across major Android brands.
- Create the Google Play listing, Data Safety declaration, app-content rating, privacy policy, support email, and permission declaration for `QUERY_ALL_PACKAGES`.
- Configure Play App Signing before the first production upload. The key used for the first public release must be retained for future updates.
- Do not sell subscriptions until a complete Google Play Billing and verified entitlement flow has been implemented and tested.
