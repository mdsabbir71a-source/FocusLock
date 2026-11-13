# FocusLock v1.1.57 release handoff

Updated 13 November 2026.

- Android package: io.focuslock.app. Version 1.1.57, version code 257.
- Exact source used for the signed build: commit 9bf2319a969fb35db0cbccb490a5dcba3f34cfba on feature/subscription-catalog. This backup branch starts at that commit.
- Production AAB: FocusLock-v1.1.57-Production.aab, ChatGPT Library file libfile_77008ecd55488191878b0a97fc2849da. SHA-256: 82ac98643e87bc6085cf84431193d22676a2f0e1513c83db376e1c0e2537c840.
- Corresponding test APK: FocusLock-v1.1.57-Quiet-Premium-Test.apk, library file libfile_1c28e2a783608191a28becee4731648b.
- User reported the v1.1.57 change works. Automated GitHub Actions run 43354742192 passed announcement policy and subscription offer selection, Android build, signatures, billing permission, absence of advertising ID permissions, and purchase endpoint authentication rejection. These do not prove full lifecycle or device behavior.
- Includes v1.1.51+ subscription flow, scrolling review cards, smoother onboarding and navigation, the local analytics JSON fix, and removed premium success dialogs. Keep error and pending-payment messages.
- Supabase project sfrwqrjwyzhabmquythi was ACTIVE_HEALTHY; verify-play-purchase v15 with JWT verification and public-announcement v6 were ACTIVE when checked. Firebase project focuslock-5a784. Never put credentials in this file.
- User previously submitted v1.1.51 to Google Play; its current review and live status are unverified. This v1.1.57 bundle has not been uploaded or released by this assistant. Check Play Console for the status and version code before submission; user can upload to Production.
- Automatic access review previously rejected opening the Google Play Console origin; do not work around that restriction.
- Real-time developer notifications and durable server-side purchase-token ownership/persistence were not implemented in the previously reviewed verification flow. Recheck current state and do not claim complete lifecycle/security validation. Real prices and trial eligibility come from Google Play.
- Source is backed up on backup/release-v1.1.57. Continue from this release or a later confirmed version, never an older build.
