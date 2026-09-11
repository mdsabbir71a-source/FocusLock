package com.focuslock.app;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/** FocusLock account entry with a simple two-choice welcome and a separate email flow. */
public class AuthActivity extends Activity {
    private static final int INK = Color.rgb(17, 24, 39);
    private static final int MUTED = Color.rgb(107, 114, 128);
    private static final int GREEN = Color.rgb(39, 91, 59);
    // A deeper version of the FocusLock green keeps the Google action branded
    // while giving it a calmer, more grounded contrast on the welcome screen.
    private static final int BRIGHT_GREEN = Color.rgb(35, 112, 64);
    private static final int BACKGROUND = Color.rgb(248, 251, 246);
    private static final int SOFT = Color.rgb(240, 248, 239);
    private static final int BORDER = Color.rgb(220, 233, 220);
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
                adviceHandler.postDelayed(this, 3800L);
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

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(Color.rgb(254, 254, 252));
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
        adviceHandler.removeCallbacksAndMessages(null);
        emailScreenVisible = false;
        email = null;
        password = null;
        signIn = null;

        ScrollView scroll = screen();
        LinearLayout root = column();
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(10), dp(22), dp(24));
        scroll.addView(root, matchWrap());

        FrameLayout hero = new FrameLayout(this);
        hero.addView(new FocusWelcomeAnimationView(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.focuslock_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams logoParams = new FrameLayout.LayoutParams(dp(80), dp(80));
        logoParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        logoParams.topMargin = dp(58);
        hero.addView(logo, logoParams);

        TextView brand = text("FocusLock", 21, INK, true);
        brand.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams brandParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brandParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        brandParams.topMargin = dp(146);
        hero.addView(brand, brandParams);
        root.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(224)));

        TextView title = text("Take back your attention.", 28, INK, true);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(-.018f);
        root.addView(title);

        TextView subtitle = text("One small boundary at a time.", 13, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, topMargin(7));

        advice = text(ENCOURAGEMENTS[adviceIndex], 12, GREEN, true);
        advice.setGravity(Gravity.CENTER);
        advice.setPadding(dp(15), dp(11), dp(15), dp(11));
        advice.setBackground(shape(SOFT, BORDER, 18));
        root.addView(advice, topMargin(18));
        adviceHandler.postDelayed(advanceAdvice, 3800L);

        LinearLayout actions = column();
        google = button("G   Continue with Google", BRIGHT_GREEN, Color.WHITE, BRIGHT_GREEN);
        google.setOnClickListener(v -> beginGoogle());
        actions.addView(google);

        signUp = button("Sign up with email   →", Color.WHITE, GREEN, BORDER);
        signUp.setOnClickListener(v -> showEmailScreen(true));
        actions.addView(signUp, topMargin(11));
        root.addView(actions, topMargin(28));

        status = statusText();
        root.addView(status, topMargin(12));
        root.addView(legalText(), topMargin(10));

        setContentView(scroll);
        animateLanding(root, hero, actions, returning);
        startLogoMotion(logo);
    }

    private void showEmailScreen(boolean create) {
        adviceHandler.removeCallbacksAndMessages(null);
        emailScreenVisible = true;
        captureDraft();
        advice = null;
        google = null;
        signIn = null;
        signUp = null;

        ScrollView scroll = screen();
        LinearLayout root = column();
        root.setPadding(dp(22), dp(18), dp(22), dp(28));
        scroll.addView(root, matchWrap());

        TextView back = text("←   Back", 13, GREEN, true);
        back.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        back.setPadding(0, dp(8), dp(12), dp(8));
        back.setOnClickListener(v -> showLanding(true));
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.focuslock_logo);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        brandRow.addView(logo, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView brand = text("FocusLock", 20, INK, true);
        LinearLayout.LayoutParams brandText = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brandText.leftMargin = dp(10);
        brandRow.addView(brand, brandText);
        root.addView(brandRow, topMargin(18));

        TextView title = text(create ? "Create your account" : "Welcome back", 28, INK, true);
        title.setLetterSpacing(-.018f);
        root.addView(title, topMargin(24));
        TextView copy = text(create
                ? "Start building a calmer relationship with your apps."
                : "Your focus plan is ready when you are.", 13, MUTED, false);
        copy.setLineSpacing(0, 1.15f);
        root.addView(copy, topMargin(7));

        LinearLayout card = column();
        card.setPadding(dp(18), dp(20), dp(18), dp(20));
        card.setBackground(shape(Color.WHITE, BORDER, 24));
        card.addView(text("Email", 11, MUTED, true));
        email = input("you@example.com", false);
        email.setText(draftEmail);
        card.addView(email, topMargin(7));
        card.addView(text("Password", 11, MUTED, true), topMargin(16));
        password = input("At least 8 characters", true);
        password.setText(draftPassword);
        card.addView(password, topMargin(7));

        Button action = button(create ? "Create account   →" : "Log in   →",
                GREEN, Color.WHITE, GREEN);
        action.setOnClickListener(v -> authenticate(create));
        if (create) signUp = action; else signIn = action;
        card.addView(action, topMargin(20));

        if (!create) {
            Button forgot = button("Forgot password?", Color.WHITE, GREEN, Color.WHITE);
            forgot.setOnClickListener(v -> requestPasswordReset());
            card.addView(forgot, topMargin(5));
        }

        TextView switchMode = text(create
                ? "Already have an account?   Log in"
                : "New to FocusLock?   Create an account", 12, GREEN, true);
        switchMode.setGravity(Gravity.CENTER);
        switchMode.setPadding(dp(8), dp(13), dp(8), dp(8));
        switchMode.setOnClickListener(v -> showEmailScreen(!create));
        card.addView(switchMode, topMargin(5));
        root.addView(card, topMargin(24));

        status = statusText();
        root.addView(status, topMargin(12));
        root.addView(legalText(), topMargin(10));

        setContentView(scroll);
        root.setAlpha(0f);
        root.setTranslationX(dp(22));
        root.animate().alpha(1f).translationX(0f).setDuration(330)
                .setInterpolator(new DecelerateInterpolator()).start();
        card.setAlpha(0f);
        card.setTranslationY(dp(20));
        card.animate().alpha(1f).translationY(0f).setStartDelay(90).setDuration(380)
                .setInterpolator(new DecelerateInterpolator()).start();
        brandRow.setAlpha(0f);
        brandRow.setTranslationY(-dp(8));
        brandRow.animate().alpha(1f).translationY(0f).setStartDelay(110).setDuration(300)
                .setInterpolator(new DecelerateInterpolator()).start();
        title.setAlpha(0f);
        title.setTranslationY(dp(10));
        title.animate().alpha(1f).translationY(0f).setStartDelay(160).setDuration(340)
                .setInterpolator(new DecelerateInterpolator()).start();
        startLogoMotion(logo);
    }

    private void authenticate(boolean create) {
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
            String verifier = randomUrlToken(48);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String challenge = Base64.encodeToString(
                    digest.digest(verifier.getBytes(StandardCharsets.US_ASCII)),
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            getSharedPreferences("focuslock_oauth", MODE_PRIVATE).edit()
                    .putString("verifier", verifier).apply();
            Uri url = Uri.parse(BuildConfig.SUPABASE_URL + "/auth/v1/authorize").buildUpon()
                    .appendQueryParameter("provider", "google")
                    .appendQueryParameter("redirect_to", CALLBACK)
                    .appendQueryParameter("code_challenge", challenge)
                    .appendQueryParameter("code_challenge_method", "s256")
                    .build();
            startActivity(new Intent(Intent.ACTION_VIEW, url));
            show("Complete Google sign-in in your browser.", false);
        } catch (Exception e) { show("Could not start Google sign-in.", true); }
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
        field.setHintTextColor(Color.rgb(156, 163, 175));
        field.setSingleLine(true);
        field.setPadding(dp(14), dp(13), dp(14), dp(13));
        field.setBackground(shape(Color.rgb(249, 251, 248), BORDER, 15));
        field.setInputType(secret
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        field.setOnFocusChangeListener((view, focused) -> {
            view.setBackground(shape(Color.rgb(249, 251, 248), focused ? BRIGHT_GREEN : BORDER, 15));
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
        value.setPadding(dp(14), dp(14), dp(14), dp(14));
        value.setBackground(shape(background, stroke, 21));
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
