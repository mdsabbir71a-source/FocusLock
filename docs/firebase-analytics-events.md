# FocusLock analytics implementation

This document describes the Firebase-enabled Android test branch. GA4 events below are individual occurrences; use unique-user metrics or a funnel exploration rather than interpreting event totals as unique people. Events can be delayed, skipped when analytics is off, or omitted if a device never uploads. No selected app name or package is included in these custom events.

| Event | Trigger |
| --- | --- |
| `focus_app_open` | Main activity startup |
| `setup_started` | Main activity starts while onboarding remains incomplete; may occur more than once per person |
| `setup_permissions_viewed` | Permission onboarding displayed |
| `setup_usage_requested`, `setup_overlay_requested`, `setup_battery_requested`, `setup_accessibility_requested` | Corresponding onboarding permission card tapped |
| `setup_usage_ready`, `setup_overlay_ready`, `setup_battery_ready`, `setup_accessibility_ready` | A previously pending onboarding permission card becomes ready while that screen is open; not counted for permissions already granted before the screen |
| `setup_completed` | Permission onboarding completed |
| `protection_started` | User saves a valid protection plan |
| `focus_pause_started` | A selected app reaches its limit and a new lock starts, without transmitting its name |
| `paywall_preview_viewed` | Preview is shown |
| `paywall_yearly_selected`, `paywall_monthly_selected` | Plan selection tapped on the preview |
| `paywall_preview_action` | Preview action tapped; this is not a checkout or purchase |
| `paywall_restore_selected` | Preview restore action tapped; no restoration is performed |
| `paywall_preview_dismissed` | Preview dismissed after being shown; not proof of why a user left |
| `analytics_check` | User queues a test event from Account |

## Crashes and opt-out

Crashlytics automatic upload is disabled in the manifest. On application startup, previously stored reports are sent only if the saved app-analytics choice is enabled; when disabled, pending reports are deleted. Crashlytics can write reports locally while uploads are disabled. App analytics and crash reporting use the same choice in Account. Crash reports can contain diagnostic stack traces, device and app information; do not add selected-app information to custom keys or logs. Crash reporting requires a real device crash/restart test and an accurate privacy policy and Google Play Data safety declaration before release.

## Purchases

The current paywall is only a preview and the app does not have an active Google Play Billing purchase flow. There is no purchase or revenue event; do not interpret a preview action as a checkout or purchase. Add billing instrumentation only after real purchase state is available and verified; avoid duplicate or unverified revenue events.
