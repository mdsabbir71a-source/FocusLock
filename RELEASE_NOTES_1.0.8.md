# FocusLock 1.0.8

- Fixed the Home-screen flow that could be marked complete even when the launcher did not add the icon.
- Added a clear **Add to Home screen** dialog before Android's system confirmation.
- Added a confirmation receiver so FocusLock records success only after the launcher reports that the shortcut was pinned.
- Detects an existing FocusLock pinned shortcut and avoids unnecessary duplicate requests.
- Keeps **Add to Home screen** in the three-dot menu for manual retries.
- Shows simple drag-to-Home instructions when a launcher does not support Android's pinned-shortcut request.
- Preserves the account, selected apps, timers, and existing blocking settings during the update.
