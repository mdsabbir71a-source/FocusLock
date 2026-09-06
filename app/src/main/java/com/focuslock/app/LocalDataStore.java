package com.focuslock.app;

import android.content.Context;

/** Clears local FocusLock data after confirmed account deletion. */
public final class LocalDataStore {
    private LocalDataStore() {}

    public static void clearAfterAccountDeletion(Context context) {
        ProtectionRestarter.cancel(context);
        LockStore.clear(context);
        AppSelectionStore.clear(context);
        RemoteConfigStore.clear(context);
        AccountStore.clear(context);
        DiagnosticStore.clearAll(context);
        context.getSharedPreferences("focuslock_onboarding", Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences("focuslock_oauth", Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences("focuslock_legal", Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences("focuslock_reliability", Context.MODE_PRIVATE).edit().clear().apply();
        SecureSessionStore.clear(context);
    }
}
