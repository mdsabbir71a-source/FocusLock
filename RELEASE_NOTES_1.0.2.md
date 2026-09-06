# FocusLock 1.0.2 — Unified master switch

The FocusLock master switch now controls the complete protection system consistently.

## Changes

- Turning FocusLock OFF immediately closes the DNS VPN tunnel and stops selected-app monitoring.
- Turning FocusLock ON restarts selected-app monitoring and DNS protection together.
- Existing Android VPN approval is reused, so users are not asked again unless Android revoked it or another VPN replaced FocusLock.
- Saving a new app/timer commitment now starts DNS protection too; previously this path could enable app blocking without starting the VPN.
- The VPN service checks the saved master state and shuts itself down if that state becomes OFF.
- VPN permission revocation now closes the tunnel cleanly.
- Preserves the account, selected apps, timers, analytics, and existing settings when installed over 1.0.1.

Package: `com.focuslock.app`  
Version code: `102`  
Version name: `1.0.2`
