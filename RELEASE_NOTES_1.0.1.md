# FocusLock 1.0.1 — Reliability fix

This update fixes selected-app blocking silently stopping after FocusLock has been active for several hours.

## Changes

- Restarts the foreground app monitor if Android or the phone manufacturer stops its process.
- Uses a real service heartbeat, so the app no longer reports protection as active when the monitor is not running.
- Restores the currently open app after a service restart, even when that app was already open before recovery.
- Adds a periodic watchdog and cross-check from the DNS protection service.
- Restores protection after phone restart when the required permissions are still enabled.
- Adds a one-tap Android battery-reliability request during setup and for existing users who have protection enabled.
- Preserves all selected apps, timers, login, analytics, and existing settings when installed over version 1.0.0.

## Install

Install this APK directly over FocusLock 1.0.0. Do not uninstall the old version. On first launch after updating, tap **Allow** when FocusLock asks to stay active in the background.

Package: `com.focuslock.app`  
Version code: `101`  
Version name: `1.0.1`
