# FocusLock release handoff — 12 November 2026

## Current state
- Android package: io.focuslock.app. Production candidate v1.1.51, version code 251.
- Exact source used for the candidate: commit 12a4e600a50eced61ec6b7a520e676332e760237 on feature/subscription-catalog. The release build enables subscription gating and Google Play billing. This backup branch starts from that exact commit.
- Signed release bundle: FocusLock-v1.1.51-Production-Candidate.aab, saved in ChatGPT Library (libfile_18cea5c353f481918b92c7f7964f2700).
- GitHub Actions run 43157983574 passed the announcement and offer tests, build, APK and bundle signature checks, billing permission check, advertising-ID permission absence check, and unauthenticated endpoint rejection check. These checks are not a complete device or subscription lifecycle test.
- The user previously tested checkout and restore with Google Play license testing. The precise v1.1.51 production build has not been verified through Google Play.

## Deployed services
- Supabase project sfrwqrjwyzhabmquythi was ACTIVE_HEALTHY when checked. verify-play-purchase version 15 was ACTIVE with JWT verification enabled; public-announcement version 6 and delete-account version 4 were also ACTIVE.
- Server-side purchase verification has been deployed and a previous invalid-token check reached Google and was rejected. Do not infer that every lifecycle/security scenario has been tested. Avoid placing credentials or purchase tokens in this file.
- Firebase project: focuslock-5a784.

## Remaining release work
- The v1.1.51 bundle has not been uploaded to production or confirmed approved by Google Play. Check the actual Play Console release/version state before proceeding; version code 251 may already have been used since this handoff.
- Google Play Console access was blocked by an automatic access review. The owner can upload the AAB in Production and submit for review; do not claim it is live without confirmation.
- Before broad rollout, verify the exact release through Play on a license tester, including purchase, restoration, restart, renewal/expiry, cancellation and temporary network failure; verify the yearly trial appears only for eligible users and real localized Play prices are accurate. The user reported previous test purchases and restore working.
- Real-time Developer Notifications and durable server-side purchase-token ownership/persistence were not implemented in the previously reviewed verification flow. Re-check current backend before claiming these are complete.
- User intent: $9.99 monthly, $39.99 yearly, eligible yearly plan has a seven-day free trial, and premium access is required. Google Play prices and eligibility remain authoritative.
- Keep existing users' stable core blocking features and the latest subscription work when making future changes. Never replace this candidate with an older build unintentionally.
