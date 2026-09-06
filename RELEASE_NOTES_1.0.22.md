# FocusLock 1.0.22

## Timer-save redirect fix

- Saving a timer no longer opens Chrome or any external browser.
- Remote update prompts are no longer shown as a side effect of app resume or a timer save.
- Updates are no longer opened automatically while the setup screen is active; the normal website/download flow remains unchanged.
- Existing account, selected apps, timer values, and permissions are preserved.

## Cause

The previous build refreshed remote configuration asynchronously in `MainActivity.onResume()`. When that request completed, it could show an update dialog whose **Update now** action launched the configured website with `ACTION_VIEW`. The callback could complete at the same time the user pressed **Save & start**, making the browser look like part of applying the timer. The timer persistence itself was not redirecting anywhere.
