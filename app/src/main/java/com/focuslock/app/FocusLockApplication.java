package com.focuslock.app;

import android.app.Application;

/** Installs a privacy-preserving crash marker when the user has opted in. */
public class FocusLockApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            DiagnosticStore.recordCrash(this, error);
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }
}
