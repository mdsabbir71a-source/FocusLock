# FocusLock for Android

FocusLock lets a user choose distracting apps, set an actual foreground-use allowance, and lock only those apps for a chosen period when the allowance is used. The master switch controls selected-app monitoring.

## Version 1.0.23

The timer actions now use vibration feedback compatible with Android 8 and later.
The previous APK made runtime reads of API 30-only fields, which could crash both
Apply time and Save & start on Android 8–10. Save also uses the guarded monitor
starter and no longer opens a notification permission prompt during the action.
See `RELEASE_NOTES_1.0.23.md` for the evidence, validation, and build method.

## Version 1.0.22 (previous attempt)

### Timer-save redirect fix

- Saving a timer no longer opens Chrome or any external browser.
- Remote update prompts are no longer shown as a side effect of app resume or a timer save.
- Updates are no longer opened automatically while the setup screen is active; the normal website/download flow remains unchanged.
- Existing account, selected apps, timer values, and permissions are preserved.

### Cause

The previous build refreshed remote configuration asynchronously in `MainActivity.onResume()`. When that request completed, it could show an update dialog whose **Update now** action launched the configured website with `ACTION_VIEW`. The callback could complete at the same time the user pressed **Save & start**, making the browser look like part of applying the timer. The timer persistence itself was not redirecting anywhere.

### Account and session reliability

- Restored the in-app Account section with account details, subscription/access status, FAQ, Privacy Policy, Terms, Contact us, password/email changes, sign-out, and account deletion.
- Hardened Supabase JWT handling with strict token validation, URL-safe Base64 padding, signed expiry (`exp`) support, and safe recovery from malformed sessions.
- Serialised refresh-token rotation and retry a request once after an expired access token, preventing launch-time requests from invalidating one another.
- Invalid sessions are cleared cleanly with a clear sign-in prompt; temporary server/network failures keep the cached access state intact.
- FocusLock branding remains the app's own; no Supabase branding is shown in the product UI.

## Version 1.0.20

### Save-screen stability

- Starting protection no longer replays stale foreground-app events, so saving a timer cannot unexpectedly hand control back to Chrome or another selected app.
- Remote update prompts wait until a later launch instead of interrupting the Save & start action.

## Version 1.0.19

- Android 8.0+ (`minSdk 26`), targeting Android 15 (`targetSdk 35`).
- Email/password and Google sign-in through Supabase.
- A compact focus-plan home screen with guided permissions, priority app tiles, an expandable full app list, and a fixed Save action.
- Polished minute/second wheel timers use animated **Use limit** and **Lock length** tabs so only one clear choice is visible at a time.
- Purposeful motion includes staggered screen entrances, tactile app and timer feedback, animated protection states, botanical background movement, pulsing coach marks, and a save-success leaf burst.
- Free access for every signed-in account. Billing can be connected later without changing account IDs.
- Password recovery, email/password updates, permanent account deletion, legal links, and support contact.
- Encrypted Android-Keystore sessions, row-level security, redacted opt-in diagnostics, consent history, remote announcements, and signed update notices.
- No Accessibility service and no `QUERY_ALL_PACKAGES` permission.
- A real monitor heartbeat, sticky-service recovery, boot restore, and a periodic watchdog keep selected-app blocking active after Android/OEM process termination.
- The status card now reports the actual monitor health instead of only the saved switch state, and repairs a stopped monitor automatically.
- The master switch pauses or restarts selected-app monitoring.
- Adult-content protection and all VPN functionality have been completely removed.
- Analytics and progress tracking are not part of the interface or account sync.
- FocusLock remains available in the normal Android app drawer and does not request or create a Home-screen shortcut.
- Both timer values stay visible with quick adjustment buttons plus an exact Hours / Minutes / Seconds scroll picker, animated digits, live preview, and light haptic feedback.
- The first-run guide now spotlights both timer cards in order: **Use limit**, then **Lock length**, before guiding the user to save.
- Authentication now begins with a calm animated welcome and only two choices: **Continue with Google** or **Sign up with email**. Email creation and returning-user login use a separate, focused screen.
- Removed the optional Focus Reset puzzle gate so FocusLock controls open normally while protection is active.
- The welcome screen now makes **Continue with Google** the colored primary action, with **Sign up with email** as the quiet secondary action.
- Removed the blocking agreement dialog from signup/login; Terms and Privacy Policy remain linked on the auth screen and consent is recorded quietly when continuing.
- Deepened the green Google sign-in button to better match the FocusLock brand.
- Fresh setups now start with a 1-minute use limit and a 10-minute lock length; existing saved timers are preserved.
- Frequently selected apps are remembered locally and surfaced earlier in the app list.
- The Account section now includes account details, subscription status, FAQ, privacy, terms, contact support, password/email changes, sign out, and account deletion.

## User setup

1. Sign in and review the linked Privacy Policy and Terms.
2. Allow Usage Access, Display Over Other Apps, notifications, and background reliability when Android asks.
3. Select one or more apps and set an allowed foreground-use time plus lock duration.
4. Save the commitment and turn FocusLock on.

Only time actually spent in a selected app counts. When the allowance expires, FocusLock sends that app to the background and displays the countdown screen when it is opened again.

## Build locally

Open this folder in a recent Android Studio, let Gradle sync, connect an Android 8.0+ phone, and click **Run**. Release builds require these environment variables:

- `FOCUSLOCK_KEYSTORE_PATH`
- `FOCUSLOCK_KEYSTORE_PASSWORD`
- `FOCUSLOCK_KEY_ALIAS`
- `FOCUSLOCK_KEY_PASSWORD`

Never commit the production keystore or its passphrase. Every public update must use the same certificate.

## GitHub Actions

The included workflow builds a debug APK on pushes and pull requests. A manual `workflow_dispatch` run can also build a production-signed APK after these GitHub repository secrets are configured:

- `FOCUSLOCK_KEYSTORE_BASE64`
- `FOCUSLOCK_KEYSTORE_PASSWORD`
- `FOCUSLOCK_KEY_ALIAS`
- `FOCUSLOCK_KEY_PASSWORD`

## Privacy and operational notes

- Selected package names and browsing activity are not uploaded. Account identity, entitlement, device/app version, aggregate daily progress, consent records, and optional redacted diagnostics can sync to Supabase.
- Users can disable permissions or uninstall the app. FocusLock is a self-control tool, not tamper-resistant device management.
- Battery behavior differs by manufacturer. Test on Pixel, Samsung, Xiaomi, Oppo/Realme, and other target devices before a broad launch.
- FocusLock asks Android to exclude it from battery optimization because selected-app monitoring must remain active while the main screen is closed. Users can revoke this in Android settings.
- The client contains only a Supabase publishable key. Service-role credentials belong only in protected server functions.
- A custom SMTP provider and CAPTCHA are recommended once sign-up volume grows; neither is required for the initial free beta.
