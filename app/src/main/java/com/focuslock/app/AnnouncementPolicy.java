package com.focuslock.app;

/** Decide completion separately for dismissals and link taps. */
final class AnnouncementPolicy {
    private AnnouncementPolicy() {}
    static boolean shouldShow(boolean enabled, boolean hasText, boolean repeat, String version, String seen, String clicked) {
        if (!enabled || !hasText || version == null || version.isEmpty()) return false;
        return !version.equals(repeat ? clicked : seen);
    }
}
