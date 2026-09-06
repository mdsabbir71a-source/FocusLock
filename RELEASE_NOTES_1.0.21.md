# FocusLock 1.0.21

## Account tools

- Restored the Account section in the main menu.
- Added account details, current subscription/access status, password and email changes, FAQ, Privacy Policy, Terms, Contact us, sign-out, and account deletion.
- Account links open with FocusLock's own copy and website paths; no backend provider branding is shown in the app UI.

## JWT/session reliability

- Validates all three JWT segments and handles URL-safe Base64 tokens with missing padding.
- Uses the signed `exp` claim for accurate access-token expiry, including already-expired sessions that need immediate refresh.
- Serializes rotating refresh-token calls so simultaneous launch requests cannot invalidate one another.
- Retries one authorized request after a 401 response with the refreshed token.
- Clears invalid/expired sessions cleanly while retaining cached access during temporary server or network failures.

## Compatibility

- Version code: 121
- Version name: 1.0.21
- Package: `com.focuslock.app`
- Signed with the same FocusLock production certificate as previous releases.
- Install over the existing app; do not uninstall first.
