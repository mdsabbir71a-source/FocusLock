# FocusLock 1.0.20

## Save-screen stability

- Fixed a foreground-event race that could treat an older Chrome event as the current app when protection started.
- Save & start now remains in FocusLock while the monitor initializes.
- Remote update prompts no longer interrupt timer saving or open the browser unexpectedly during that action.
