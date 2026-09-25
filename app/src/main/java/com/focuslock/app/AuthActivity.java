package com.focuslock.app;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/** FocusLock account entry with a simple two-choice welcome and a separate email flow. */
public class AuthActivity extends Activity {
    private int INK = Color.rgb(19, 42, 28);
    private int MUTED = Color.rgb(91, 107, 95);
    private int GREEN = Color.rgb(31, 107, 59);
    private int BRIGHT_GREEN = Color.rgb(23, 83, 46);
    private int BACKGROUND = Color.rgb(247, 245, 239);
    private int SOFT = Color.rgb(251, 243, 228);
    private int BORDER = Color.rgb(205, 220, 205);
    private boolean darkTheme;
    private static final String CALLBACK = "focuslock://auth/callback";
    private static final String CONSENT_VERSION = "2026-08-18";
    private static final String[] ENCOURAGEMENTS = {
            "Small boundaries create a calmer mind.",
            "Your attention belongs to the life you choose.",
            "A mindful pause can change your whole day.",
            "Protect your focus. Let better habits grow."
    };

    private final Handler adviceHandler = new Handler(Looper.getMainLooper());
    private final Runnable advanceAdvice = new Runnable() {
        @Override public void run() {
            if (advice == null || advice.getWindowToken() == null) return;
            advice.animate().alpha(0f).translationY(-dp(5)).setDuration(180).withEndAction(() -> {
                adviceIndex = (adviceIndex + 1) % ENCOURAGEMENTS.length;
                advice.setText(ENCOURAGEMENTS[adviceIndex]);
                advice.setTranslationY(dp(5));
                advice.animate().alpha(1f).translationY(0f).setDuration(260).start();
                adviceHandler.postDelayed(this, 6500L);
            }).start();
        }
    };

    private EditText email;
    private EditText password;
    private TextView status;
    private TextView advice;
    private Button signIn;
    private Button signUp;
    private Button google;
    private String draftEmail = "";
    private String draftPassword = "";
    private int adviceIndex;
    private boolean emailScreenVisible;
    private CredentialManager credentialManager;
    private final Executor mainExecutor = command -> new Handler(Looper.getMainLooper()).post(command);
    // Credential Manager can return after the user has moved to the email flow.
    // Keep each Google request scoped to the screen that started it so a late
    // cancellation/result can never overwrite an email sign-in message.
    private int authAttemptGeneration;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        refreshTheme();
        applySystemBars();
        showLanding(false);
        handleCallback(getIntent());
        if (getIntent().getData() == null && SecureSessionStore.hasSession(this)) finishAuthentication();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        showLanding(false);
        handleCallback(intent);
    }

    @Override protected void onDestroy() {
        adviceHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (emailScreenVisible) showLanding(true);
        else moveTaskToBack(true);
    }

    private void showLanding(boolean returning) {
        invalidateGoogleAttempt();
        refreshTheme();
        applySystemBars();
        adviceHandler.removeCallbacksAndMessages(null);
        emailScreenVisible = false;
        email = null;
        password = null;
        signIn = null;
        advice = null;

        FrameLayout scene = new FrameLayout(this);
        scene.setBackgroundColor(BACKGROUND);
        scene.addView(new FocusWelcomeAnimationView(this, true), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll = screen();
        scroll.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout root = column();
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(26), dp(10), dp(26), dp(22));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scene.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(horizonWordmark(false), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));

        TextView title = text("Take back your attention.", 35, INK, true);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(-.035f);
        root.addView(title);

        TextView subtitle = text("One small boundary at a time.\nChoose what gets in, and when.", 15, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(0, 1.25f);
        root.addView(subtitle, topMargin(10));

        View openSpace = new View(this);
        root.addView(openSpace, new LinearLayout.LayoutParams(1, 0, 1f));

        LinearLayout actions = column();
        google = button("G   Continue with Google", BRIGHT_GREEN, Color.WHITE, BRIGHT_GREEN);
        google.setOnClickListener(v -> beginGoogle());
        actions.addView(google);

        signUp = button("Sign up with email", darkTheme ? Color.rgb(35, 50, 42) : Color.argb(238, 247, 245, 239), INK, darkTheme ? Color.rgb(65, 160, 92) : Color.argb(82, 31, 107, 59));
        signUp.setOnClickListener(v -> showEmailScreen(true));
        actions.addView(signUp, topMargin(11));
        root.addView(actions, matchWrap());

        status = statusText();
        root.addView(status, topMargin(8));
        root.addView(legalText(), topMargin(8));

        setContentView(scene);
        animateLanding(root, root, actions, returning);
    }

    private void showEmailScreen(boolean create) {
        invalidateGoogleAttempt();
        refreshTheme();
        applySystemBars();
        adviceHandler.removeCallbacksAndMessages(null);
        emailScreenVisible = true;
        captureDraft();
        advice = null;
        google = null;
        signIn = null;
        signUp = null;

        FrameLayout scene = new FrameLayout(this);
        scene.setBackgroundColor(BACKGROUND);
        FocusWelcomeAnimationView fullPageArt = new FocusWelcomeAnimationView(this, true);
        scene.addView(fullPageArt, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll = screen();
        scroll.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout root = column();
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(26), dp(8), dp(26), dp(24));
        scroll.addView(root, matchWrap());
        scene.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(horizonWordmark(true), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(136)));

        TextView title = text(create ? "Create your account" : "Welcome back", 29, INK, true);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(-.03f);
        root.addView(title);
        TextView subtitle = text(create
                ? "One small boundary at a time."
                : "Your focus plan is ready when you are.", 14, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, topMargin(9));

        LinearLayout card = column();
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(shape(darkTheme ? Color.argb(238, 31, 45, 37) : Color.argb(230, 247, 245, 239), darkTheme ? Color.argb(115, 100, 201, 121) : Color.argb(65, 31, 107, 59), 24));
        card.addView(text("EMAIL ADDRESS", 10, GREEN, true));
        email = input("you@example.com", false);
        email.setText(draftEmail);
        card.addView(email, topMargin(6));
        card.addView(text("PASSWORD", 10, GREEN, true), topMargin(14));
        password = input("At least 8 characters", true);
        password.setText(draftPassword);
        card.addView(password, topMargin(6));

        Button action = button(create ? "Create my focus space" : "Continue to FocusLock",
                BRIGHT_GREEN, Color.WHITE, BRIGHT_GREEN);
        action.setOnClickListener(v -> authenticate(create));
        if (create) signUp = action; else signIn = action;
        card.addView(action, topMargin(18));

        if (!create) {
            TextView forgot = text("Forgot password?", 12, GREEN, true);
            forgot.setGravity(Gravity.CENTER);
            forgot.setPadding(dp(8), dp(14), dp(8), dp(4));
            forgot.setOnClickListener(v -> requestPasswordReset());
            card.addView(forgot);
        }
        TextView switchMode = text(create
                ? "Already a member?  Log in"
                : "New here?  Create an account", 12, GREEN, true);
        switchMode.setGravity(Gravity.CENTER);
        switchMode.setPadding(dp(8), dp(15), dp(8), dp(3));
        switchMode.setOnClickListener(v -> showEmailScreen(!create));
        card.addView(switchMode);
        root.addView(card, topMargin(25));

        status = statusText();
        root.addView(status, topMargin(10));
        root.addView(legalText(), topMargin(6));

        setContentView(scene);
        root.setAlpha(0f);
        root.setTranslationY(dp(12));
        root.animate().alpha(1f).translationY(0f).setDuration(360)
                .setInterpolator(new DecelerateInterpolator()).start();
        ObjectAnimator artworkBreath = ObjectAnimator.ofFloat(fullPageArt, "alpha", .82f, 1f, .82f);
        artworkBreath.setDuration(4300);
        artworkBreath.setRepeatCount(ObjectAnimator.INFINITE);
        artworkBreath.setInterpolator(new AccelerateDecelerateInterpolator());
        artworkBreath.start();
    }

    /** Native version of the supplied horizon heading: centered mark and wordmark. */
    private FrameLayout horizonWordmark(boolean showBack) {
        FrameLayout header = new FrameLayout(this);
        header.addView(new FocusWelcomeAnimationView(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.focuslock_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        brandRow.addView(logo, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView brand = text("FocusLock", 20, INK, true);
        brand.setPadding(dp(12), 0, 0, 0);
        brandRow.addView(brand, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams rowParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        rowParams.topMargin = dp(36);
        header.addView(brandRow, rowParams);

        if (showBack) {
            TextView back = text("←", 24, GREEN, false);
            back.setGravity(Gravity.CENTER);
            back.setContentDescription("Back");
            back.setOnClickListener(v -> showLanding(true));
            FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.START);
            backParams.topMargin = dp(20);
            header.addView(back, backParams);
        }
        // A very small breathing/float motion keeps the supplied horizon
        // heading alive without distracting from sign-in.
        startLogoMotion(logo);
        return header;
    }

    private void authenticate(boolean create) {
        invalidateGoogleAttempt();
        captureDraft();
        String enteredEmail = draftEmail.trim();
        String enteredPassword = draftPassword;
        if (!enteredEmail.contains("@")) { show("Enter a valid email address.", true); return; }
        if (enteredPassword.length() < 8) { show("Use a password with at least 8 characters.", true); return; }
        busy(create ? "Creating your account…" : "Logging you in…");
        SupabaseApi.Callback<SupabaseApi.AuthResult> callback = (result, error) -> {
            if (error != null) { idle(); show(error, true); return; }
            if (result.needsEmailConfirmation) {
                showEmailScreen(false);
                show("Account created. Check your email, confirm it, then log in.", false);
            } else finishAuthentication();
        };
        if (create) SupabaseApi.signUp(this, enteredEmail, enteredPassword, callback);
        else SupabaseApi.signIn(this, enteredEmail, enteredPassword, callback);
    }

    private void beginGoogle() {
        try {
            busy("Choose a Google account…");
            final int attempt = ++authAttemptGeneration;
            final String nonce = randomUrlToken(32);
            // Google stores the SHA-256 form in the ID token, while Supabase
            // validates the original value sent with signInWithIdToken.
            final String googleNonce = sha256Hex(nonce);
            GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(
                    BuildConfig.GOOGLE_WEB_CLIENT_ID).setNonce(googleNonce).build();
            GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(option).build();
            credentialManager = CredentialManager.create(this);
            credentialManager.getCredentialAsync(this, request, null, mainExecutor,
                    new CredentialManagerCallback<androidx.credentials.GetCredentialResponse,
                            GetCredentialException>() {
                        @Override public void onResult(androidx.credentials.GetCredentialResponse response) {
                            if (!isCurrentGoogleAttempt(attempt)) return;
                            Credential returned = response.getCredential();
                            if (!(returned instanceof CustomCredential)
                                    || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                    .equals(returned.getType())) {
                                idle();
                                show("Google did not return an account. Please try again.", true);
                                return;
                            }
                            try {
                                GoogleIdTokenCredential googleCredential = GoogleIdTokenCredential
                                        .createFrom(((CustomCredential) returned).getData());
                                busy("Signing you in…");
                                SupabaseApi.signInWithGoogleIdToken(AuthActivity.this,
                                        googleCredential.getIdToken(), nonce, (result, error) -> {
                                            if (!isCurrentGoogleAttempt(attempt)) return;
                                            if (error != null) { idle(); show(error, true); }
                                            else finishAuthentication();
                                        });
                            } catch (Exception error) {
                                if (!isCurrentGoogleAttempt(attempt)) return;
                                idle();
                                show("Google sign-in could not be completed. Please try again.", true);
                            }
                        }

                        @Override public void onError(GetCredentialException error) {
                            if (!isCurrentGoogleAttempt(attempt)) return;
                            idle();
                            show("Google account selection was cancelled.", false);
                        }
                    });
        } catch (Exception e) { idle(); show("Could not start Google sign-in.", true); }
    }

    private void invalidateGoogleAttempt() {
        authAttemptGeneration++;
    }

    private boolean isCurrentGoogleAttempt(int attempt) {
        return attempt == authAttemptGeneration && !emailScreenVisible && !isFinishing();
    }

    private void handleCallback(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data == null || !"focuslock".equals(data.getScheme()) || !"auth".equals(data.getHost())) return;
        String error = data.getQueryParameter("error_description");
        if (error == null) error = data.getQueryParameter("error");
        if (error != null) { idle(); show(error, true); return; }

        Map<String, String> fragment = parseFragment(data.getFragment());
        String type = data.getQueryParameter("type");
        if (type == null) type = fragment.get("type");
        final boolean recovery = "recovery".equals(type);
        String code = data.getQueryParameter("code");
        SharedPreferences oauth = getSharedPreferences("focuslock_oauth", MODE_PRIVATE);
        if (code != null) {
            String verifier = oauth.getString("verifier", "");
            oauth.edit().clear().apply();
            if (verifier.isEmpty()) { show("Google sign-in expired. Please try again.", true); return; }
            busy("Finishing Google sign-in…");
            SupabaseApi.exchangePkce(this, code, verifier, (result, exchangeError) -> {
                if (exchangeError != null) { idle(); show(exchangeError, true); }
                else if (recovery) showNewPasswordDialog();
                else finishAuthentication();
            });
            return;
        }

        String access = fragment.get("access_token");
        String refresh = fragment.get("refresh_token");
        if (access != null && refresh != null) {
            busy("Finishing sign-in…");
            long expires = parseLong(fragment.get("expires_in"), 3600);
            SupabaseApi.importSessionTokens(this, access, refresh, expires, (result, importError) -> {
                if (importError != null) { idle(); show(importError, true); }
                else if (recovery) showNewPasswordDialog();
                else finishAuthentication();
            });
        }
    }

    private void finishAuthentication() {
        // The legal notice remains compactly visible on the auth screen. Do
        // not interrupt signup with a second agreement dialog; record the
        // acknowledgement in the background and continue to the app.
        if (!getSharedPreferences("focuslock_legal", MODE_PRIVATE)
                .getString("consent_version", "").equals(CONSENT_VERSION)) {
            getSharedPreferences("focuslock_legal", MODE_PRIVATE).edit()
                    .putString("consent_version", CONSENT_VERSION).apply();
            SupabaseApi.recordConsent(this, CONSENT_VERSION, (saved, error) -> { });
        }
        finishAuthenticationAfterConsent();
    }

    private void finishAuthenticationAfterConsent() {
        busy("Preparing your FocusLock space…");
        SupabaseApi.refreshEntitlement(this, (allowed, error) -> {
            if (Boolean.TRUE.equals(allowed)) {
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
            } else {
                idle();
                show(error == null ? AccessStore.reason(this) : error, true);
            }
        });
    }

    private void requestPasswordReset() {
        captureDraft();
        String enteredEmail = draftEmail.trim();
        if (!enteredEmail.contains("@")) { show("Enter your email address first.", true); return; }
        busy("Sending a secure reset link…");
        SupabaseApi.requestPasswordReset(enteredEmail, (sent, error) -> {
            idle();
            if (!Boolean.TRUE.equals(sent)) show(error, true);
            else show("Reset link sent. Open it on this phone to choose a new password.", false);
        });
    }

    private void showNewPasswordDialog() {
        EditText newPassword = input("New password (8+ characters)", true);
        int padding = dp(20);
        newPassword.setPadding(padding, dp(12), padding, dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Choose a new password")
                .setView(newPassword)
                .setPositiveButton("Save password", null)
                .setNegativeButton("Cancel", (ignored, which) -> idle())
                .setCancelable(false)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = newPassword.getText().toString();
            if (value.length() < 8) { newPassword.setError("Use at least 8 characters"); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            SupabaseApi.updatePassword(this, value, (saved, error) -> {
                if (!Boolean.TRUE.equals(saved)) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    newPassword.setError(error);
                    return;
                }
                dialog.dismiss();
                show("Password updated securely.", false);
                finishAuthentication();
            });
        }));
        dialog.show();
    }

    private void openPage(String path) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PUBLIC_SITE_URL + path))); }
        catch (Exception ignored) { show("Could not open the FocusLock website.", true); }
    }

    private void captureDraft() {
        if (email != null) draftEmail = email.getText().toString();
        if (password != null) draftPassword = password.getText().toString();
    }

    private void busy(String message) {
        show(message, false);
        setEnabled(signIn, false);
        setEnabled(signUp, false);
        setEnabled(google, false);
    }

    private void idle() {
        setEnabled(signIn, true);
        setEnabled(signUp, true);
        setEnabled(google, true);
    }

    private void setEnabled(Button button, boolean enabled) {
        if (button == null) return;
        button.setEnabled(enabled);
        button.animate().alpha(enabled ? 1f : .58f).setDuration(140).start();
    }

    private void show(String message, boolean error) {
        if (status == null) return;
        status.setText(message == null ? "" : message);
        status.setTextColor(error ? Color.rgb(174, 54, 54) : GREEN);
        status.setAlpha(0f);
        status.setTranslationY(dp(4));
        status.animate().alpha(1f).translationY(0f).setDuration(180).start();
    }

    /** Applies the supplied horizon palette to the device's light/dark setting. */
    private void refreshTheme() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        darkTheme = mode == Configuration.UI_MODE_NIGHT_YES;
        if (darkTheme) {
            BACKGROUND = Color.rgb(22, 32, 26);
            INK = Color.rgb(231, 240, 232);
            MUTED = Color.rgb(174, 190, 177);
            GREEN = Color.rgb(101, 205, 123);
            BRIGHT_GREEN = Color.rgb(31, 107, 59);
            SOFT = Color.rgb(35, 50, 42);
            BORDER = Color.rgb(66, 97, 76);
        } else {
            BACKGROUND = Color.rgb(247, 245, 239);
            INK = Color.rgb(19, 42, 28);
            MUTED = Color.rgb(91, 107, 95);
            GREEN = Color.rgb(31, 107, 59);
            BRIGHT_GREEN = Color.rgb(23, 83, 46);
            SOFT = Color.rgb(251, 243, 228);
            BORDER = Color.rgb(205, 220, 205);
        }
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        int flags = darkTheme ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (!darkTheme && android.os.Build.VERSION.SDK_INT >= 26) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private ScrollView screen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        return scroll;
    }

    private TextView statusText() {
        TextView value = text("", 12, MUTED, true);
        value.setGravity(Gravity.CENTER);
        value.setLineSpacing(0, 1.12f);
        value.setMinHeight(dp(20));
        return value;
    }

    private TextView legalText() {
        String value = "By continuing, you agree to our Terms and Privacy Policy";
        SpannableString span = new SpannableString(value);
        int termsStart = value.indexOf("Terms");
        int privacyStart = value.indexOf("Privacy Policy");
        span.setSpan(link("/terms"), termsStart, termsStart + 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(link("/privacy"), privacyStart, value.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView legal = text("", 10, MUTED, false);
        legal.setText(span);
        legal.setGravity(Gravity.CENTER);
        legal.setLineSpacing(0, 1.12f);
        legal.setPadding(dp(8), dp(6), dp(8), dp(4));
        legal.setMovementMethod(LinkMovementMethod.getInstance());
        legal.setHighlightColor(Color.TRANSPARENT);
        return legal;
    }

    private ClickableSpan link(String path) {
        return new ClickableSpan() {
            @Override public void onClick(View widget) { openPage(path); }
            @Override public void updateDrawState(TextPaint ds) {
                ds.setColor(GREEN);
                ds.setUnderlineText(true);
            }
        };
    }

    private EditText input(String hint, boolean secret) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setTextSize(14);
        field.setTextColor(INK);
        field.setHintTextColor(darkTheme ? Color.rgb(155, 170, 160) : Color.rgb(156, 163, 175));
        field.setSingleLine(true);
        field.setPadding(dp(14), dp(13), dp(14), dp(13));
        field.setBackground(shape(darkTheme ? Color.rgb(35, 50, 42) : Color.rgb(251, 250, 246), BORDER, 16));
        field.setInputType(secret
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        field.setOnFocusChangeListener((view, focused) -> {
            view.setBackground(shape(darkTheme ? Color.rgb(35, 50, 42) : Color.rgb(251, 250, 246), focused ? BRIGHT_GREEN : BORDER, 16));
            view.animate().scaleX(focused ? 1.012f : 1f).scaleY(focused ? 1.012f : 1f)
                    .setDuration(150).start();
        });
        return field;
    }

    private Button button(String label, int background, int foreground, int stroke) {
        Button value = new Button(this);
        value.setText(label);
        value.setTextSize(13);
        value.setTextColor(foreground);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setAllCaps(false);
        value.setGravity(Gravity.CENTER);
        value.setMinHeight(0);
        value.setMinimumHeight(0);
        value.setPadding(dp(14), dp(15), dp(14), dp(15));
        value.setMinHeight(dp(56));
        value.setMinimumHeight(dp(56));
        value.setBackground(shape(background, stroke, 28));
        value.setStateListAnimator(null);
        value.setOnTouchListener((view, event) -> {
            if (!view.isEnabled()) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                view.animate().cancel();
                view.animate().scaleX(.972f).scaleY(.972f).setDuration(75).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                view.animate().cancel();
                view.animate().scaleX(1f).scaleY(1f).setDuration(150)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
            return false;
        });
        return value;
    }

    private void animateLanding(LinearLayout root, View hero, View actions, boolean returning) {
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(300).start();
        hero.setAlpha(0f);
        hero.setTranslationY(returning ? -dp(8) : dp(14));
        hero.animate().alpha(1f).translationY(0f).setDuration(480)
                .setInterpolator(new DecelerateInterpolator()).start();
        actions.setAlpha(0f);
        actions.setTranslationY(dp(22));
        actions.animate().alpha(1f).translationY(0f).setStartDelay(150).setDuration(430)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void startLogoMotion(ImageView logo) {
        ObjectAnimator breatheX = ObjectAnimator.ofFloat(logo, "scaleX", 1f, 1.045f, 1f);
        ObjectAnimator breatheY = ObjectAnimator.ofFloat(logo, "scaleY", 1f, 1.045f, 1f);
        ObjectAnimator floatY = ObjectAnimator.ofFloat(logo, "translationY", 0f, -dp(4), 0f);
        breatheX.setDuration(2700);
        breatheY.setDuration(2700);
        floatY.setDuration(3300);
        breatheX.setRepeatCount(ObjectAnimator.INFINITE);
        breatheY.setRepeatCount(ObjectAnimator.INFINITE);
        floatY.setRepeatCount(ObjectAnimator.INFINITE);
        breatheX.setInterpolator(new AccelerateDecelerateInterpolator());
        breatheY.setInterpolator(new AccelerateDecelerateInterpolator());
        floatY.setInterpolator(new AccelerateDecelerateInterpolator());
        AnimatorSet set = new AnimatorSet();
        set.playTogether(breatheX, breatheY, floatY);
        set.start();
    }

    private String randomUrlToken(int bytes) {
        byte[] value = new byte[bytes];
        new SecureRandom().nextBytes(value);
        return Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(hash.length * 2);
            for (byte part : hash) output.append(String.format("%02x", part & 0xff));
            return output.toString();
        } catch (Exception error) {
            throw new IllegalStateException("Could not secure Google sign-in", error);
        }
    }

    private Map<String, String> parseFragment(String fragment) {
        Map<String, String> values = new HashMap<>();
        if (fragment == null) return values;
        for (String pair : fragment.split("&")) {
            int split = pair.indexOf('=');
            if (split > 0) values.put(Uri.decode(pair.substring(0, split)), Uri.decode(pair.substring(split + 1)));
        }
        return values;
    }

    private long parseLong(String value, long fallback) {
        try { return Long.parseLong(value); } catch (Exception ignored) { return fallback; }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout column() {
        LinearLayout value = new LinearLayout(this);
        value.setOrientation(LinearLayout.VERTICAL);
        return value;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams topMargin(int margin) {
        LinearLayout.LayoutParams value = matchWrap();
        value.topMargin = dp(margin);
        return value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
