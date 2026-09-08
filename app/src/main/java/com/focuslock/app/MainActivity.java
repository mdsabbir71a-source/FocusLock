package com.focuslock.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int INK = Color.rgb(17, 24, 39);
    private static final int MUTED = Color.rgb(107, 114, 128);
    private static final int FAINT = Color.rgb(156, 163, 175);
    private static final int VIOLET = Color.rgb(52, 116, 76);
    private static final int SOFT_VIOLET = Color.rgb(240, 248, 239);
    private static final int BORDER = Color.rgb(220, 233, 220);
    private static final int GREEN = Color.rgb(45, 130, 78);
    private static final int GOLD = Color.rgb(244, 226, 171);
    private static final int BACKGROUND = Color.rgb(248, 251, 246);
    private static final int REQUEST_NOTIFICATIONS = 42;

    private final List<CheckBox> appChecks = new ArrayList<>();
    private final List<View> optionalAppTiles = new ArrayList<>();
    private TextView status;
    private TextView selectedCount;
    private EditText graceInput;
    private EditText graceSecondsInput;
    private EditText durationInput;
    private EditText durationSecondsInput;
    private LinearLayout permissionRow;
    private LinearLayout setupCard;
    private Button masterButton;
    private View masterCard;
    private View statusOrb;
    private ImageView headerLogo;
    private Button saveButton;
    private ScrollView mainScroll;
    private FrameLayout screenRoot;
    private CoachMarkOverlay coachOverlay;
    private LinearLayout contentRoot;
    private View permissionSectionAnchor;
    private View appSectionAnchor;
    private GridLayout appGrid;
    private Button showAppsButton;
    private TextView appSectionHint;
    private boolean allAppsExpanded;
    private View settingsAnchor;
    private CheckBox guideAppTarget;
    private int guideAppScore = Integer.MAX_VALUE;
    private View guideTimerTarget;
    private View guideLockTimerTarget;
    private TextView timerSummary;
    private View useTimerCard;
    private View lockTimerCard;
    private TextView useTimerValue;
    private TextView lockTimerValue;
    private CheckBox lockFocusLockCheck;
    private View selfLockCard;
    private Button easySetupButton;
    private TextView permissionNote;
    private TextView setupTitle;
    private LinearLayout guideCard;
    private TextView guideTitle;
    private TextView guideBody;
    private TextView guideHint;
    private final Handler guideHandler = new Handler();
    private int currentGuideStep;
    private boolean guidedSetup;
    private boolean skipNotificationPrompt;
    private boolean newGuideIntro;
    private int waitingForSpecialPermission;
    private boolean refreshingAccess;
    private boolean refreshingRemoteConfig;
    private boolean permissionPrimerShowing;
    private boolean commitmentInProgress;
    private SuccessBurstView successBurst;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SecureSessionStore.hasSession(this) || !AccessStore.isAllowed(this)) {
            openAuthentication();
            return;
        }
        SharedPreferences onboarding = getSharedPreferences("focuslock_onboarding", MODE_PRIVATE);
        boolean welcomed = onboarding.getBoolean("welcome_seen", false);
        newGuideIntro = !welcomed;
        if (newGuideIntro) {
            onboarding.edit().putBoolean("interactive_guide_v104_seen", true).putBoolean("guide_complete", false).apply();
        }
        setContentView(buildUi());
        if (!welcomed) new Handler().postDelayed(this::showFirstLaunchSetup, 550);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!SecureSessionStore.hasSession(this)) { openAuthentication(); return; }
        if (!AccessStore.isAllowed(this)) { stopProtectionForAccess(); openAuthentication(); return; }
        if (!LockStore.isEnabled(this)) {
            stopService(new Intent(this, FocusMonitorService.class));
            ProtectionRestarter.cancel(this);
        }
        if (LockStore.isEnabled(this) && usageAccessEnabled() && Settings.canDrawOverlays(this)
                && RemoteConfigStore.appBlockingEnabled(this)) {
            startSavedMonitoring();
        }
        refreshRemoteAccess();
        refreshLiveConfig();
        refreshStatus();
        refreshPermissionCards();
        refreshMasterButton();
        new Handler().postDelayed(this::maybeExplainBatteryReliability, 650L);
        new Handler().postDelayed(this::maybeShowSelfLockGuide, 1100L);
        if (waitingForSpecialPermission != 0) {
            int returningFrom = waitingForSpecialPermission;
            waitingForSpecialPermission = 0;
            new Handler().postDelayed(() -> {
                boolean allowed = returningFrom == 1 ? usageAccessEnabled() : Settings.canDrawOverlays(this);
                if (returningFrom == 3) allowed = batteryReliabilityEnabled();
                if (!allowed) {
                    guidedSetup = false;
                    toast(returningFrom == 3
                            ? "Battery reliability was skipped. FocusLock will still retry automatically."
                            : "That permission was not enabled. Tap Easy Setup whenever you're ready.");
                } else {
                    continueEasySetup();
                }
            }, 300);
        }
    }

    private View buildUi() {
        screenRoot = new FrameLayout(this);
        screenRoot.setBackgroundColor(BACKGROUND);

        AmbientNatureView ambient = new AmbientNatureView(this);
        ambient.setAlpha(.7f);
        screenRoot.addView(ambient, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scroll = new ScrollView(this);
        mainScroll = scroll;
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout root = column();
        contentRoot = root;
        root.setPadding(dp(18), dp(14), dp(18), dp(122));
        scroll.addView(root, matchWrap());

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        headerLogo = new ImageView(this);
        headerLogo.setImageResource(R.drawable.focuslock_logo);
        headerLogo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        header.addView(headerLogo, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout brandBox = column();
        brandBox.addView(text("FocusLock", 18, INK, true));
        brandBox.addView(text("Protect your attention", 10, MUTED, false), topMargin(1));
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        brandLp.leftMargin = dp(10);
        header.addView(brandBox, brandLp);
        TextView menu = text("•••", 15, VIOLET, true);
        menu.setContentDescription("Open menu");
        menu.setGravity(Gravity.CENTER);
        menu.setPadding(dp(13), dp(7), dp(13), dp(9));
        menu.setBackground(shape(Color.WHITE, BORDER, 19));
        menu.setOnClickListener(v -> showMainMenu());
        attachPressAnimation(menu);
        header.addView(menu);
        root.addView(header, matchWrap());

        LinearLayout master = column();
        masterCard = master;
        master.setPadding(dp(17), dp(17), dp(17), dp(15));
        master.setBackground(shape(Color.WHITE, BORDER, 26));
        LinearLayout masterTop = row();
        masterTop.setGravity(Gravity.CENTER_VERTICAL);
        statusOrb = new View(this);
        statusOrb.setBackground(shape(GREEN, GREEN, 12));
        masterTop.addView(statusOrb, new LinearLayout.LayoutParams(dp(15), dp(15)));
        LinearLayout masterCopy = column();
        masterCopy.addView(text("Protection", 19, INK, true));
        masterCopy.addView(text("Your focus boundary", 10, MUTED, false), topMargin(2));
        LinearLayout.LayoutParams masterCopyLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        masterCopyLp.leftMargin = dp(11);
        masterTop.addView(masterCopy, masterCopyLp);
        masterButton = button("ON", GREEN, Color.WHITE);
        masterButton.setTextSize(12);
        masterButton.setMinWidth(dp(76));
        masterButton.setContentDescription("Turn protection on or off");
        masterButton.setOnClickListener(v -> toggleMaster());
        attachPressAnimation(masterButton);
        masterTop.addView(masterButton);
        master.addView(masterTop);
        status = text("Checking…", 11, MUTED, true);
        status.setPadding(0, dp(12), 0, dp(2));
        master.addView(status);
        root.addView(master, topMargin(18));

        LinearLayout selfLockRow = row();
        selfLockCard = selfLockRow;
        selfLockRow.setGravity(Gravity.CENTER_VERTICAL);
        selfLockRow.setPadding(dp(14), dp(11), dp(9), dp(11));
        selfLockRow.setBackground(shape(Color.rgb(249, 252, 248), BORDER, 20));
        LinearLayout selfLockCopy = column();
        selfLockCopy.addView(text("Lock FocusLock during a pause", 13, INK, true));
        selfLockCopy.addView(text("Optional · keep settings unavailable until the pause ends", 10, MUTED, false), topMargin(2));
        selfLockRow.addView(selfLockCopy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        lockFocusLockCheck = new CheckBox(this);
        lockFocusLockCheck.setChecked(LockStore.lockFocusLock(this));
        lockFocusLockCheck.setContentDescription("Lock FocusLock during a pause");
        lockFocusLockCheck.setOnCheckedChangeListener((buttonView, checked) -> markDirty());
        selfLockRow.addView(lockFocusLockCheck, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));
        selfLockRow.setOnClickListener(v -> lockFocusLockCheck.performClick());
        root.addView(selfLockRow, topMargin(10));

        setupCard = column();
        setupCard.setPadding(dp(15), dp(14), dp(15), dp(14));
        setupCard.setBackground(shape(SOFT_VIOLET, BORDER, 22));
        setupCard.setLayoutTransition(new LayoutTransition());
        setupTitle = text("Quick setup", 15, INK, true);
        setupCard.addView(setupTitle);
        permissionSectionAnchor = setupCard;
        permissionRow = row();
        setupCard.addView(permissionRow, topMargin(9));
        easySetupButton = button("Continue  →", INK, Color.WHITE);
        easySetupButton.setTextSize(13);
        easySetupButton.setOnClickListener(v -> startEasySetup());
        attachPressAnimation(easySetupButton);
        setupCard.addView(easySetupButton, topMargin(10));
        permissionNote = text("About one minute", 10, MUTED, false);
        permissionNote.setGravity(Gravity.CENTER);
        setupCard.addView(permissionNote, topMargin(7));
        root.addView(setupCard, topMargin(11));

        guideCard = column();
        guideTitle = text("", 1, Color.TRANSPARENT, false);
        guideBody = text("", 1, Color.TRANSPARENT, false);
        guideHint = text("", 1, Color.TRANSPARENT, false);
        guideCard.addView(guideTitle);
        guideCard.addView(guideBody);
        guideCard.addView(guideHint);
        guideCard.setVisibility(View.GONE);

        LinearLayout appsCard = column();
        appsCard.setPadding(dp(14), dp(14), dp(14), dp(13));
        appsCard.setBackground(shape(Color.WHITE, BORDER, 24));
        LinearLayout chooseHeader = row();
        chooseHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView appStep = text("1", 12, Color.WHITE, true);
        appStep.setGravity(Gravity.CENTER);
        appStep.setBackground(shape(GREEN, GREEN, 17));
        chooseHeader.addView(appStep, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout chooseCopy = column();
        chooseCopy.addView(text("Choose apps", 17, INK, true));
        appSectionHint = text("Tap to select", 10, MUTED, false);
        chooseCopy.addView(appSectionHint, topMargin(1));
        LinearLayout.LayoutParams chooseCopyLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        chooseCopyLp.leftMargin = dp(10);
        chooseHeader.addView(chooseCopy, chooseCopyLp);
        selectedCount = text("0 selected", 10, VIOLET, true);
        selectedCount.setPadding(dp(9), dp(5), dp(9), dp(5));
        selectedCount.setBackground(shape(SOFT_VIOLET, BORDER, 14));
        chooseHeader.addView(selectedCount);
        appsCard.addView(chooseHeader);
        appSectionAnchor = appsCard;

        appGrid = new GridLayout(this);
        appGrid.setColumnCount(3);
        appGrid.setLayoutTransition(new LayoutTransition());
        addLaunchableApps(appGrid);
        appsCard.addView(appGrid, topMargin(9));
        showAppsButton = button("Show all apps  ↓", SOFT_VIOLET, VIOLET);
        showAppsButton.setTextSize(11);
        showAppsButton.setOnClickListener(v -> toggleAllApps());
        attachPressAnimation(showAppsButton);
        if (!optionalAppTiles.isEmpty()) {
            showAppsButton.setText("Show all " + appChecks.size() + " apps  ↓");
            appsCard.addView(showAppsButton, topMargin(8));
        }
        root.addView(appsCard, topMargin(17));

        LinearLayout settings = column();
        settingsAnchor = settings;
        settings.setPadding(dp(14), dp(14), dp(14), dp(13));
        settings.setBackground(shape(Color.WHITE, BORDER, 24));
        LinearLayout timerHeader = row();
        timerHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView timerStep = text("2", 12, Color.WHITE, true);
        timerStep.setGravity(Gravity.CENTER);
        timerStep.setBackground(shape(GREEN, GREEN, 17));
        timerHeader.addView(timerStep, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout timerCopy = column();
        timerCopy.addView(text("Set the timer", 17, INK, true));
        timerCopy.addView(text("Tap + or − to adjust", 10, MUTED, false), topMargin(1));
        LinearLayout.LayoutParams timerCopyLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        timerCopyLp.leftMargin = dp(10);
        timerHeader.addView(timerCopy, timerCopyLp);
        settings.addView(timerHeader);

        graceInput = numberInput(String.valueOf(LockStore.allowance(this) / 60_000));
        graceSecondsInput = numberInput(String.valueOf((LockStore.allowance(this) % 60_000) / 1000));
        durationInput = numberInput(String.valueOf(LockStore.lockDuration(this) / 60_000));
        durationSecondsInput = numberInput(String.valueOf((LockStore.lockDuration(this) % 60_000) / 1000));

        useTimerCard = stepperTimerCard("Use limit", "Time allowed before blocking", true,
                LockStore.allowance(this));
        settings.addView(useTimerCard, topMargin(11));

        TextView timerConnector = text("↓   THEN", 10, GREEN, true);
        timerConnector.setGravity(Gravity.CENTER);
        timerConnector.setPadding(0, dp(7), 0, dp(7));
        settings.addView(timerConnector);
        ObjectAnimator connectorMotion = ObjectAnimator.ofFloat(timerConnector, "translationY", 0f, dp(4), 0f);
        connectorMotion.setDuration(1300);
        connectorMotion.setRepeatCount(ObjectAnimator.INFINITE);
        connectorMotion.setInterpolator(new AccelerateDecelerateInterpolator());
        connectorMotion.start();

        lockTimerCard = stepperTimerCard("Lock length", "How long the selected apps stay blocked", false,
                LockStore.lockDuration(this));
        settings.addView(lockTimerCard);
        guideLockTimerTarget = lockTimerCard;
        guideTimerTarget = useTimerCard;

        timerSummary = text(timerSummaryText(LockStore.allowance(this), LockStore.lockDuration(this)), 12, VIOLET, true);
        timerSummary.setGravity(Gravity.CENTER);
        timerSummary.setPadding(dp(11), dp(10), dp(11), dp(10));
        timerSummary.setBackground(shape(SOFT_VIOLET, BORDER, 16));
        settings.addView(timerSummary, topMargin(10));
        root.addView(settings, topMargin(11));

        screenRoot.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout actionBar = row();
        actionBar.setGravity(Gravity.CENTER_VERTICAL);
        actionBar.setPadding(dp(18), dp(12), dp(18), dp(16));
        actionBar.setBackground(shape(Color.rgb(253, 254, 252), BORDER, 0));
        saveButton = button("Save & start  →", INK, Color.WHITE);
        saveButton.setTextSize(15);
        saveButton.setMinHeight(dp(56));
        saveButton.setOnClickListener(v -> startCommitment());
        attachPressAnimation(saveButton);
        actionBar.addView(saveButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        screenRoot.addView(actionBar, actionParams);

        refreshPermissionCards();
        refreshMasterButton();
        refreshStatus();
        if (LockStore.packages(this).isEmpty()) markDirty(); else markSaved();
        reveal(header, 20);
        reveal(master, 90);
        reveal(appsCard, 170);
        reveal(settings, 250);
        reveal(useTimerCard, 330);
        reveal(lockTimerCard, 430);
        startLogoAnimation();
        mainScroll.post(this::resumeGuide);
        return screenRoot;
    }

    private void showMainMenu() {
        // External links remain available from the account/legal screens, but
        // nothing in the setup flow should navigate away from FocusLock.
        String[] items = {"Guide", "Account"};
        new AlertDialog.Builder(this)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) restartGuide();
                    else showAccountDialog();
                })
                .show();
    }

    private void showFirstLaunchSetup() {
        if (isFinishing()) return;
        getSharedPreferences("focuslock_onboarding", MODE_PRIVATE).edit()
                .putBoolean("welcome_seen", true).apply();
        updateGuideStep(1, easySetupButton);
    }

    private void openAuthentication() {
        startActivity(new Intent(this, AuthActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }


    private void refreshRemoteAccess() {
        if (refreshingAccess) return;
        refreshingAccess = true;
        SupabaseApi.refreshEntitlement(this, (allowed, error) -> {
            refreshingAccess = false;
            if (!Boolean.TRUE.equals(allowed)) {
                stopProtectionForAccess();
                if (!isFinishing()) showAccessUnavailable(error == null ? AccessStore.reason(this) : error);
            }
        });
    }

    private void refreshLiveConfig() {
        if (refreshingRemoteConfig) return;
        refreshingRemoteConfig = true;
        SupabaseApi.refreshRemoteConfig(this, (updated, error) -> {
            refreshingRemoteConfig = false;
            applyRemoteAvailability();
            applyRemoteDefaultsIfUnused();
            // Do not open or present an external update action as a side effect
            // of onResume. This callback can finish while the user is saving
            // a timer, so the normal website/download flow handles updates.
            if (!commitmentInProgress) showAnnouncementIfNeeded();
        });
    }

    private void applyRemoteAvailability() {
        if (!RemoteConfigStore.appBlockingEnabled(this)) {
            stopService(new Intent(this, FocusMonitorService.class));
            ProtectionRestarter.cancel(this);
        } else if (LockStore.isEnabled(this) && usageAccessEnabled() && Settings.canDrawOverlays(this)) {
            startSavedMonitoring();
        }
    }

    private void applyRemoteDefaultsIfUnused() {
        if (!LockStore.packages(this).isEmpty() || graceInput == null || durationInput == null) return;
        int use = RemoteConfigStore.defaultUseSeconds(this);
        int pause = RemoteConfigStore.defaultLockSeconds(this);
        graceInput.setText(String.valueOf(use / 60));
        graceSecondsInput.setText(String.valueOf(use % 60));
        durationInput.setText(String.valueOf(pause / 60));
        durationSecondsInput.setText(String.valueOf(pause % 60));
        if (timerSummary != null) timerSummary.setText(timerSummaryText(use * 1000L, pause * 1000L));
        updateTimerSteppers(use * 1000L, pause * 1000L);
    }

    private boolean showUpdateIfNeeded() {
        // The main setup surface must never launch a browser as a side effect
        // of saving a timer or resuming the activity. Updates are delivered by
        // the normal download flow, so this legacy hook is intentionally inert.
        return false;
    }

    private void showAnnouncementIfNeeded() {
        if (!RemoteConfigStore.shouldShowAnnouncement(this) || isFinishing()) return;
        new AlertDialog.Builder(this)
                .setTitle(RemoteConfigStore.announcementTitle(this))
                .setMessage(RemoteConfigStore.announcementBody(this))
                .setPositiveButton("Got it", (dialog, which) -> RemoteConfigStore.markAnnouncementSeen(this))
                .setOnCancelListener(dialog -> RemoteConfigStore.markAnnouncementSeen(this))
                .show();
    }

    private void stopProtectionForAccess() {
        stopService(new Intent(this, FocusMonitorService.class));
        ProtectionRestarter.cancel(this);
    }

    private void showAccessUnavailable(String reason) {
        new AlertDialog.Builder(this)
                .setTitle("FocusLock access unavailable")
                .setMessage(reason == null ? "Please verify your account and try again." : reason)
                .setPositiveButton("Try again", (dialog, which) -> openAuthentication())
                .setNegativeButton("Sign out", (dialog, which) -> {
                    SupabaseApi.logout(this);
                    openAuthentication();
                })
                .setCancelable(false)
                .show();
    }

    private void showAccountDialog() {
        LinearLayout panel = column();
        panel.setPadding(dp(18), dp(2), dp(18), dp(8));

        String email = AccountStore.email(this);
        String provider = AccountStore.provider(this);
        TextView identity = text(email.isEmpty() ? "Signed in securely" : email,
                15, INK, true);
        TextView identityDetail = text(provider.equalsIgnoreCase("google")
                ? "Google account"
                : "FocusLock account", 10, MUTED, false);
        LinearLayout identityCard = column();
        identityCard.setPadding(dp(14), dp(13), dp(14), dp(13));
        identityCard.setBackground(shape(SOFT_VIOLET, BORDER, 18));
        identityCard.addView(identity);
        identityCard.addView(identityDetail, topMargin(3));
        panel.addView(identityCard);

        panel.addView(text("ACCOUNT", 10, VIOLET, true), topMargin(16));
        panel.addView(accountRow("Account details", "Email, sign-in method, app version", v -> showAccountDetailsDialog()), topMargin(7));
        panel.addView(accountRow("Manage subscription", friendlyPlanName() + "  ·  Manage access", v -> showSubscriptionDialog()), topMargin(7));
        panel.addView(accountRow("Change password", "Update your password", v -> showChangePasswordDialog()), topMargin(7));
        panel.addView(accountRow("Change email", "Update your sign-in email", v -> showChangeEmailDialog()), topMargin(7));

        panel.addView(text("HELP & PRIVACY", 10, VIOLET, true), topMargin(16));
        panel.addView(accountRow("FocusLock FAQ", "Answers about blocking and timers", v -> showFaqDialog()), topMargin(7));
        panel.addView(accountRow("Privacy policy", "How FocusLock handles your data", v -> openWebsitePath("/privacy")), topMargin(7));
        panel.addView(accountRow("Terms of service", "The rules for using FocusLock", v -> openWebsitePath("/terms")), topMargin(7));
        panel.addView(accountRow("Contact us", "Get help from the FocusLock team", v -> sendSupportEmail()), topMargin(7));

        panel.addView(text("SECURITY", 10, VIOLET, true), topMargin(16));
        panel.addView(accountRow("Sign out", "Sign out on this device", v -> signOut()), topMargin(7));
        panel.addView(accountRow("Delete account and data", "Permanently remove your account", v -> showDeleteAccountDialog()), topMargin(7));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("Account")
                .setView(scroll)
                .setNegativeButton("Close", null)
                .show();
    }

    private View accountRow(String title, String detail, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(11), dp(12), dp(11));
        row.setBackground(shape(Color.WHITE, BORDER, 17));
        LinearLayout copy = column();
        copy.addView(text(title, 12, INK, true));
        copy.addView(text(detail, 9, MUTED, false), topMargin(2));
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = text("›", 22, VIOLET, false);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(36)));
        row.setOnClickListener(click);
        attachPressAnimation(row);
        return row;
    }

    private String friendlyPlanName() {
        String level = AccessStore.level(this);
        if (level == null || level.trim().isEmpty() || "free".equalsIgnoreCase(level)) return "Free access";
        return level.substring(0, 1).toUpperCase() + level.substring(1).toLowerCase() + " plan";
    }

    private void showAccountDetailsDialog() {
        SecureSessionStore.Session session = SecureSessionStore.get(this);
        String email = AccountStore.email(this);
        String provider = AccountStore.provider(this);
        String method = provider.equalsIgnoreCase("google") ? "Google" : "Email";
        String accountId = session == null || session.userId.length() < 8
                ? "Unavailable" : session.userId.substring(0, 8) + "…";
        String details = "Email\n" + (email.isEmpty() ? "Not available on this device" : email)
                + "\n\nSign-in method\n" + method
                + "\n\nAccount ID\n" + accountId
                + "\n\nApp version\n" + BuildConfig.VERSION_NAME;
        new AlertDialog.Builder(this)
                .setTitle("Account details")
                .setMessage(details)
                .setPositiveButton("Done", null)
                .show();
    }

    private void showSubscriptionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Subscription")
                .setMessage("Current plan: " + friendlyPlanName()
                        + "\n\nFocusLock is currently free to use. When paid plans are enabled, subscription management will appear in this section.")
                .setPositiveButton("Open website", (dialog, which) -> openWebsitePath("/"))
                .setNegativeButton("Close", null)
                .show();
    }

    private void showFaqDialog() {
        TextView faq = text(
                "How does FocusLock work?\n"
                        + "Choose apps, set a use limit, and choose how long they stay locked.\n\n"
                        + "What counts toward my limit?\n"
                        + "Only time spent in the selected app counts.\n\n"
                        + "What if protection stops?\n"
                        + "Open the main screen and use the permission repair prompts.\n\n"
                        + "Can I change my timers?\n"
                        + "Yes. Tap either timer and scroll the hours, minutes, or seconds wheel.\n\n"
                        + "Does FocusLock use a VPN?\n"
                        + "No. FocusLock does not use a VPN or filter web content.",
                12, INK, false);
        faq.setLineSpacing(0, 1.2f);
        faq.setPadding(dp(20), dp(6), dp(20), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(faq, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("FocusLock FAQ")
                .setView(scroll)
                .setPositiveButton("Done", null)
                .show();
    }

    private void showChangePasswordDialog() {
        EditText field = numberlessSecretInput("New password (8+ characters)");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Change password")
                .setView(field)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = field.getText().toString();
            if (value.length() < 8) { field.setError("Use at least 8 characters"); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            SupabaseApi.updatePassword(this, value, (saved, error) -> {
                if (!Boolean.TRUE.equals(saved)) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    field.setError(error);
                } else {
                    dialog.dismiss();
                    toast("Password updated.");
                }
            });
        }));
        dialog.show();
    }

    private void showChangeEmailDialog() {
        EditText field = numberlessEmailInput("New email address");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Change email")
                .setMessage("You may be asked to confirm both your current and new email addresses.")
                .setView(field)
                .setPositiveButton("Send confirmation", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = field.getText().toString().trim();
            if (!value.contains("@")) { field.setError("Enter a valid email"); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            SupabaseApi.updateEmail(this, value, (saved, error) -> {
                if (!Boolean.TRUE.equals(saved)) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    field.setError(error);
                } else {
                    dialog.dismiss();
                    toast("Check your email to confirm the change.");
                }
            });
        }));
        dialog.show();
    }

    private void showDiagnosticsDialog() {
        boolean enabled = DiagnosticStore.enabled(this);
        new AlertDialog.Builder(this)
                .setTitle("Anonymous diagnostics")
                .setMessage("When enabled, FocusLock may send a crash type, app version, Android version and device model. It never includes selected apps, browsing activity, passwords or screen content.")
                .setPositiveButton(enabled ? "Turn off" : "Turn on", (dialog, which) -> {
                    DiagnosticStore.setEnabled(this, !enabled);
                    toast("Anonymous diagnostics " + (!enabled ? "enabled." : "disabled."));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteAccountDialog() {
        EditText confirmation = new EditText(this);
        confirmation.setHint("Type DELETE");
        confirmation.setSingleLine(true);
        confirmation.setPadding(dp(20), dp(12), dp(20), dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Permanently delete account?")
                .setMessage("This deletes your FocusLock account, synced progress, device records and access history. It cannot be undone. Type DELETE to confirm.")
                .setView(confirmation)
                .setPositiveButton("Delete permanently", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!"DELETE".equals(confirmation.getText().toString().trim())) {
                confirmation.setError("Type DELETE exactly");
                return;
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            SupabaseApi.deleteAccount(this, (deleted, error) -> {
                if (!Boolean.TRUE.equals(deleted)) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    confirmation.setError(error);
                    return;
                }
                stopProtectionForAccess();
                LocalDataStore.clearAfterAccountDeletion(this);
                dialog.dismiss();
                openAuthentication();
            });
        }));
        dialog.show();
    }

    private void signOut() {
        stopProtectionForAccess();
        SupabaseApi.logout(this);
        openAuthentication();
    }

    private void openWebsitePath(String path) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PUBLIC_SITE_URL + path))); }
        catch (Exception ignored) { toast("Could not open the FocusLock website."); }
    }

    private void sendSupportEmail() {
        Intent email = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + BuildConfig.SUPPORT_EMAIL));
        email.putExtra(Intent.EXTRA_SUBJECT, "FocusLock support — Android " + BuildConfig.VERSION_NAME);
        try { startActivity(email); }
        catch (Exception ignored) { toast("Email support at " + BuildConfig.SUPPORT_EMAIL); }
    }

    private EditText numberlessSecretInput(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setPadding(dp(20), dp(12), dp(20), dp(12));
        return field;
    }

    private EditText numberlessEmailInput(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        field.setPadding(dp(20), dp(12), dp(20), dp(12));
        return field;
    }

    private void toggleMaster() {
        if (masterCard != null) {
            masterCard.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            masterCard.animate().cancel();
            masterCard.animate().scaleX(.985f).scaleY(.985f).setDuration(90)
                    .withEndAction(() -> masterCard.animate().scaleX(1f).scaleY(1f)
                            .setDuration(170).start()).start();
        }
        boolean enable = !LockStore.isEnabled(this);
        if (enable && !AccessStore.isAllowed(this)) {
            toast("Please verify your free FocusLock account first.");
            openAuthentication();
            return;
        }
        LockStore.setEnabled(this, enable);
        if (!enable) {
            stopService(new Intent(this, FocusMonitorService.class));
            ProtectionRestarter.cancel(this);
            toast("FocusLock is paused.");
        } else {
            if (!usageAccessEnabled() || !Settings.canDrawOverlays(this)) {
                startEasySetup();
            } else {
                startSavedMonitoring();
                toast("FocusLock is starting.");
            }
        }
        refreshMasterButton();
        refreshStatus();
        if (!LockStore.packages(this).isEmpty()) markSaved();
    }

    private void refreshMasterButton() {
        if (masterButton == null) return;
        boolean enabled = LockStore.isEnabled(this);
        masterButton.setText(enabled ? "ON" : "OFF");
        masterButton.setTextColor(enabled ? Color.WHITE : MUTED);
        masterButton.setBackground(shape(enabled ? GREEN : Color.rgb(238, 241, 236), enabled ? GREEN : BORDER, 22));
        masterButton.animate().cancel();
        masterButton.setRotation(enabled ? -2f : 2f);
        masterButton.animate().rotation(0f).scaleX(1f).scaleY(1f)
                .setInterpolator(new DecelerateInterpolator()).setDuration(260).start();
        if (statusOrb != null) {
            statusOrb.setBackground(shape(enabled ? GREEN : FAINT, enabled ? GREEN : FAINT, 12));
            statusOrb.animate().cancel();
            statusOrb.setScaleX(.72f);
            statusOrb.setScaleY(.72f);
            statusOrb.animate().scaleX(1.25f).scaleY(1.25f).setDuration(240)
                    .withEndAction(() -> statusOrb.animate().scaleX(1f).scaleY(1f)
                            .setDuration(260).start()).start();
        }
    }

    private void startSavedMonitoring() {
        // A rejected service request must not close the timer screen. Reuse the
        // permission-aware starter, which also avoids restarting a healthy monitor.
        try {
            ProtectionRestarter.ensureMonitorRunning(this);
        } catch (RuntimeException error) {
            DiagnosticStore.record(this, "monitor_start_deferred", error.getClass().getSimpleName());
        }
        new Handler().postDelayed(this::refreshStatus, 1_200L);
    }

    private void refreshPermissionCards() {
        if (permissionRow == null) return;
        permissionRow.removeAllViews();
        boolean usage = usageAccessEnabled();
        boolean overlay = Settings.canDrawOverlays(this);
        View usageCard = permissionCard("Usage Access", "", usage, v -> {
            waitingForSpecialPermission = 1;
            showPermissionPrimer("Allow Usage Access", "Find FocusLock and turn it on.", () ->
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        });
        View overlayCard = permissionCard("Gentle Lock", "", overlay, v -> {
            waitingForSpecialPermission = 2;
            showPermissionPrimer("Allow Gentle Lock", "Turn on “Display over other apps”.", () ->
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()))));
        });
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        left.rightMargin = dp(5);
        permissionRow.addView(usageCard, left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        right.leftMargin = dp(4);
        right.rightMargin = dp(4);
        permissionRow.addView(overlayCard, right);
        boolean battery = batteryReliabilityEnabled();
        View batteryCard = permissionCard("Background", "", battery, v -> {
            waitingForSpecialPermission = 3;
            showPermissionPrimer("Keep FocusLock active", "Tap Allow on the next screen.", this::requestBatteryReliability);
        });
        LinearLayout.LayoutParams third = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        third.leftMargin = dp(4);
        permissionRow.addView(batteryCard, third);
        boolean approvalsReady = usage && overlay && battery;
        if (setupTitle != null) {
            int ready = (usage ? 1 : 0) + (overlay ? 1 : 0) + (battery ? 1 : 0);
            setupTitle.setText("Quick setup  ·  " + ready + "/3");
        }
        setSetupCardVisible(!approvalsReady);
        if (easySetupButton != null) {
            easySetupButton.setText(usage && overlay ? "Allow background use  →" : "Continue  →");
            easySetupButton.setAlpha(1f);
            easySetupButton.setEnabled(!approvalsReady);
        }
        if (permissionNote != null) {
            permissionNote.setVisibility(approvalsReady ? View.GONE : View.VISIBLE);
            permissionNote.setText(!battery && usage && overlay ? "Keeps protection reliable" : "About one minute");
        }
    }

    private View permissionCard(String title, String copy, boolean enabled, View.OnClickListener click) {
        LinearLayout card = column();
        card.setPadding(dp(11), dp(11), dp(11), dp(11));
        card.setBackground(shape(Color.WHITE, enabled ? Color.rgb(167, 243, 208) : BORDER, 18));
        card.addView(text(enabled ? "✓" : "○", 14, enabled ? GREEN : VIOLET, true));
        card.addView(text(title, 12, INK, true), topMargin(5));
        Button action = button(enabled ? "Ready" : "Allow", enabled ? Color.rgb(236, 253, 245) : INK, enabled ? GREEN : Color.WHITE);
        action.setEnabled(!enabled);
        action.setOnClickListener(click);
        card.addView(action, topMargin(7));
        return card;
    }

    private void addLaunchableApps(GridLayout grid) {
        PackageManager pm = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(launcher, 0);
        Map<String, ResolveInfo> unique = new LinkedHashMap<>();
        for (ResolveInfo info : resolved) {
            String pkg = info.activityInfo.packageName;
            if (!pkg.equals(getPackageName())) unique.put(pkg, info);
        }
        Set<String> saved = LockStore.packages(this);
        List<ResolveInfo> apps = new ArrayList<>(unique.values());
        apps.sort(Comparator
                .comparingInt((ResolveInfo a) -> saved.contains(a.activityInfo.packageName) ? 0 : 1)
                .thenComparingInt(a -> -AppSelectionStore.count(this, a.activityInfo.packageName))
                .thenComparingInt(a -> appPriority(a.activityInfo.packageName))
                .thenComparing(a -> a.loadLabel(pm).toString().toLowerCase()));
        int visibleTiles = 0;
        for (ResolveInfo info : apps) {
            String pkg = info.activityInfo.packageName;
            CheckBox check = new CheckBox(this);
            check.setTag(pkg);
            String label = info.loadLabel(pm).toString();
            check.setContentDescription(label);
            check.setText(label);
            check.setTextSize(10);
            check.setTextColor(INK);
            check.setGravity(Gravity.CENTER);
            check.setButtonDrawable(null);
            check.setPadding(dp(6), dp(10), dp(6), dp(8));
            check.setMaxLines(2);
            try {
                Drawable icon = info.loadIcon(pm);
                icon.setBounds(0, 0, dp(34), dp(34));
                check.setCompoundDrawables(null, icon, null, null);
                check.setCompoundDrawablePadding(dp(7));
            } catch (Exception ignored) {}
            check.setChecked(saved.contains(pkg));
            styleAppTile(check);
            check.setOnCheckedChangeListener((button, checked) -> {
                styleAppTile(check);
                refreshSelectedCount();
                markDirty();
                check.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                check.animate().cancel();
                check.setRotation(checked ? -1.2f : 1.2f);
                check.animate().rotation(0f).scaleX(1.06f).scaleY(1.06f).setDuration(120)
                        .withEndAction(() -> check.animate().scaleX(1f).scaleY(1f)
                                .setDuration(170).start()).start();
                if (selectedAppCount() == 0) {
                    updateGuideStep(2, appSectionAnchor);
                } else if (checked) {
                    maybeGuideToTime();
                }
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(96);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            grid.addView(check, lp);
            boolean initiallyVisible = visibleTiles < 6 || saved.contains(pkg);
            if (initiallyVisible) {
                visibleTiles++;
                int guideScore = appPriority(pkg) + (saved.contains(pkg) ? 100 : 0);
                if (guideAppTarget == null || guideScore < guideAppScore) {
                    guideAppTarget = check;
                    guideAppScore = guideScore;
                }
            }
            else {
                check.setVisibility(View.GONE);
                optionalAppTiles.add(check);
            }
            appChecks.add(check);
        }
        refreshSelectedCount();
    }

    private void styleAppTile(CheckBox check) {
        check.setBackground(shape(check.isChecked() ? SOFT_VIOLET : Color.WHITE, check.isChecked() ? VIOLET : BORDER, 18));
        CharSequence label = check.getContentDescription();
        check.setText((check.isChecked() ? "✓  " : "") + (label == null ? "App" : label.toString()));
        if (Build.VERSION.SDK_INT >= 21) check.setBackgroundTintList(null);
    }

    private int appPriority(String pkg) {
        String value = pkg == null ? "" : pkg.toLowerCase();
        if (value.contains("instagram")) return 0;
        if (value.contains("tiktok")) return 1;
        if (value.contains("facebook") || value.contains("katana")) return 2;
        if (value.contains("youtube")) return 3;
        if (value.contains("snapchat")) return 4;
        if (value.contains("twitter") || value.equals("com.x.android")) return 5;
        if (value.contains("reddit")) return 6;
        if (value.contains("chrome")) return 7;
        return 100;
    }

    private void refreshSelectedCount() {
        if (selectedCount == null) return;
        int count = selectedAppCount();
        selectedCount.setText(count == 0 ? "None" : count + " selected");
        selectedCount.setTextColor(count == 0 ? MUTED : VIOLET);
        if (appSectionHint != null) {
            appSectionHint.setText(count == 0 ? "Tap to select" : "Ready to protect");
            appSectionHint.animate().cancel();
            appSectionHint.setAlpha(.35f);
            appSectionHint.animate().alpha(1f).setDuration(220).start();
        }
    }

    private int selectedAppCount() {
        int count = 0;
        for (CheckBox check : appChecks) if (check.isChecked()) count++;
        return count;
    }

    private void toggleAllApps() {
        if (optionalAppTiles.isEmpty()) return;
        allAppsExpanded = !allAppsExpanded;
        if (appGrid != null) appGrid.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        for (int i = 0; i < optionalAppTiles.size(); i++) {
            View tile = optionalAppTiles.get(i);
            if (allAppsExpanded) {
                tile.setVisibility(View.VISIBLE);
                tile.setAlpha(0f);
                tile.setScaleX(.88f);
                tile.setScaleY(.88f);
                tile.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setStartDelay(Math.min(180, i * 18L)).setDuration(220).start();
            } else {
                tile.animate().alpha(0f).scaleX(.9f).scaleY(.9f).setDuration(120)
                        .withEndAction(() -> {
                            tile.setVisibility(View.GONE);
                            tile.setAlpha(1f);
                            tile.setScaleX(1f);
                            tile.setScaleY(1f);
                        }).start();
            }
        }
        showAppsButton.setText(allAppsExpanded ? "Show fewer apps  ↑"
                : "Show all " + appChecks.size() + " apps  ↓");
        showAppsButton.animate().rotation(allAppsExpanded ? -1f : 1f).setDuration(120)
                .withEndAction(() -> showAppsButton.animate().rotation(0f).setDuration(120).start()).start();
    }

    private void setSetupCardVisible(boolean visible) {
        if (setupCard == null) return;
        setupCard.animate().cancel();
        if (visible) {
            if (setupCard.getVisibility() != View.VISIBLE) {
                setupCard.setVisibility(View.VISIBLE);
                setupCard.setAlpha(0f);
                setupCard.setTranslationY(-dp(10));
                setupCard.animate().alpha(1f).translationY(0f).setDuration(260).start();
            }
        } else if (setupCard.getVisibility() == View.VISIBLE) {
            setupCard.animate().alpha(0f).translationY(-dp(8)).setDuration(190)
                    .withEndAction(() -> {
                        setupCard.setVisibility(View.GONE);
                        setupCard.setAlpha(1f);
                        setupCard.setTranslationY(0f);
                    }).start();
        }
    }

    private void attachPressAnimation(View view) {
        view.setOnTouchListener((v, event) -> {
            if (!v.isEnabled()) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.animate().cancel();
                v.animate().scaleX(.965f).scaleY(.965f).setDuration(80).start();
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.animate().cancel();
                v.animate().scaleX(1f).scaleY(1f).setInterpolator(new DecelerateInterpolator())
                        .setDuration(150).start();
            }
            return false;
        });
    }

    private void startLogoAnimation() {
        if (headerLogo == null) return;
        ObjectAnimator floatUp = ObjectAnimator.ofFloat(headerLogo, "translationY", 0f, -dp(3), 0f);
        ObjectAnimator sway = ObjectAnimator.ofFloat(headerLogo, "rotation", -1.8f, 1.8f, -1.8f);
        floatUp.setDuration(2600);
        sway.setDuration(3400);
        floatUp.setRepeatCount(ObjectAnimator.INFINITE);
        sway.setRepeatCount(ObjectAnimator.INFINITE);
        floatUp.setInterpolator(new AccelerateDecelerateInterpolator());
        sway.setInterpolator(new AccelerateDecelerateInterpolator());
        AnimatorSet set = new AnimatorSet();
        set.playTogether(floatUp, sway);
        set.start();
    }

    private void playSuccessCelebration() {
        toast("Your focus boundary is active.");
        // KEYBOARD_TAP exists on every supported Android version. Older builds
        // read CONFIRM as a runtime field, causing NoSuchFieldError below API 30.
        saveButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        saveButton.setEnabled(false);
        saveButton.setText("You're protected  ✓");
        saveButton.setTextColor(Color.WHITE);
        saveButton.setBackground(shape(GREEN, GREEN, 24));
        saveButton.setAlpha(1f);
        saveButton.setScaleX(.94f);
        saveButton.setScaleY(.94f);
        saveButton.animate().scaleX(1.03f).scaleY(1.03f).setDuration(180)
                .withEndAction(() -> saveButton.animate().scaleX(1f).scaleY(1f)
                        .setDuration(220).start()).start();
        if (successBurst != null && successBurst.getParent() == screenRoot) {
            screenRoot.removeView(successBurst);
        }
        successBurst = new SuccessBurstView(this);
        screenRoot.addView(successBurst, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        successBurst.start(() -> {
            if (successBurst != null && successBurst.getParent() == screenRoot) {
                screenRoot.removeView(successBurst);
            }
            successBurst = null;
        });
        guideHandler.postDelayed(this::markSaved, 1250);
    }

    private void startCommitment() {
        commitmentInProgress = true;
        if (!AccessStore.isAllowed(this)) { commitmentInProgress = false; openAuthentication(); return; }
        if (!RemoteConfigStore.appBlockingEnabled(this)) { commitmentInProgress = false; toast("Selected-app blocking is temporarily unavailable."); return; }
        Set<String> selected = new HashSet<>();
        for (CheckBox check : appChecks) if (check.isChecked()) selected.add((String) check.getTag());
        if (selected.isEmpty()) {
            commitmentInProgress = false;
            toast("Choose at least one app first.");
            updateGuideStep(2, appSectionAnchor);
            return;
        }
        long grace = parseDuration(graceInput, graceSecondsInput, "use time");
        long duration = parseDuration(durationInput, durationSecondsInput, "pause time");
        if (grace < 1 || duration < 1) {
            commitmentInProgress = false;
            updateGuideStep(3, settingsAnchor);
            return;
        }
        if (!usageAccessEnabled() || !Settings.canDrawOverlays(this)) {
            commitmentInProgress = false;
            toast("Let's finish the required permissions first.");
            startEasySetup();
            return;
        }
        LockStore.configure(this, selected, grace, duration);
        LockStore.setLockFocusLock(this, lockFocusLockCheck != null && lockFocusLockCheck.isChecked());
        for (String packageName : selected) AppSelectionStore.record(this, packageName);
        LockStore.setEnabled(this, true);
        // Notification permission is handled by setup, not while saving a plan.
        startSavedMonitoring();
        playSuccessCelebration();
        updateGuideStep(6, saveButton);
        getSharedPreferences("focuslock_onboarding", MODE_PRIVATE).edit().putBoolean("guide_complete", true).apply();
        guideHandler.postDelayed(() -> {
            if (guideCard != null && !isFinishing()) {
                guideCard.animate().alpha(0f).setDuration(300).withEndAction(() -> guideCard.setVisibility(View.GONE)).start();
            }
        }, 1800);
        refreshMasterButton();
        refreshStatus();
    }

    private void refreshStatus() {
        if (status == null) return;
        Set<String> packages = LockStore.packages(this);
        if (!LockStore.isEnabled(this)) {
            status.setText("○  Paused");
            status.setTextColor(MUTED);
        } else if (packages.isEmpty()) {
            status.setText("○  Choose an app to begin");
            status.setTextColor(MUTED);
        } else if (!usageAccessEnabled() || !Settings.canDrawOverlays(this)) {
            status.setText("!  Setup needed");
            status.setTextColor(VIOLET);
        } else if (!MonitorHealthStore.isHealthy(this)) {
            status.setText("↻  Reconnecting…");
            status.setTextColor(VIOLET);
            ProtectionRestarter.ensureMonitorRunning(this);
        } else {
            status.setText("●  Active  ·  " + packages.size() + " app" + (packages.size() == 1 ? "" : "s")
                    + "  ·  " + friendly(LockStore.allowance(this)) + " limit");
            status.setTextColor(GREEN);
        }
    }

    private void startEasySetup() {
        guidedSetup = true;
        skipNotificationPrompt = false;
        continueEasySetup();
    }

    private void continueEasySetup() {
        if (!guidedSetup) return;
        if (!usageAccessEnabled()) {
            waitingForSpecialPermission = 1;
            showPermissionPrimer("Allow Usage Access", "Find FocusLock and turn it on.", () ->
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            waitingForSpecialPermission = 2;
            showPermissionPrimer("Allow Gentle Lock", "Turn on “Display over other apps”.", () ->
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName()))));
            return;
        }
        if (!batteryReliabilityEnabled()) {
            waitingForSpecialPermission = 3;
            showPermissionPrimer("Keep FocusLock active", "Tap Allow on the next screen.", this::requestBatteryReliability);
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && !skipNotificationPrompt
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return;
        }
        guidedSetup = false;
        skipNotificationPrompt = false;
        toast("Setup complete.");
        scrollToBoundarySetup();
    }

    private void showPermissionPrimer(String title, String message, Runnable action) {
        if (permissionPrimerShowing || isFinishing()) return;
        permissionPrimerShowing = true;
        LinearLayout panel = column();
        panel.setPadding(dp(22), dp(22), dp(22), dp(18));
        panel.addView(text(title, 21, INK, true));
        panel.addView(text(message, 13, MUTED, false), topMargin(7));
        LinearLayout example = row();
        example.setGravity(Gravity.CENTER_VERTICAL);
        example.setPadding(dp(14), dp(12), dp(14), dp(12));
        example.setBackground(shape(Color.rgb(247, 249, 246), BORDER, 18));
        example.addView(text("FocusLock", 14, INK, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView toggle = text("●", 22, Color.WHITE, true);
        toggle.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        toggle.setPadding(dp(18), 0, dp(5), 0);
        toggle.setBackground(shape(GREEN, GREEN, 18));
        example.addView(toggle, new LinearLayout.LayoutParams(dp(58), dp(32)));
        panel.addView(example, topMargin(18));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(panel)
                .setPositiveButton("Enable", null)
                .setNegativeButton("Later", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button enable = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            enable.setTextColor(GREEN);
            enable.setOnClickListener(v -> {
                permissionPrimerShowing = false;
                dialog.dismiss();
                action.run();
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                permissionPrimerShowing = false;
                guidedSetup = false;
                waitingForSpecialPermission = 0;
                dialog.dismiss();
            });
        });
        dialog.setOnCancelListener(ignored -> {
            permissionPrimerShowing = false;
            guidedSetup = false;
            waitingForSpecialPermission = 0;
        });
        dialog.show();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (guidedSetup) {
                if (!granted) {
                    skipNotificationPrompt = true;
                    toast("Notifications are off, but FocusLock can still run.");
                }
                continueEasySetup();
            } else if (!granted) {
                toast("Notifications are off, but FocusLock can still run.");
            }
        }
    }

    private boolean usageAccessEnabled() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        ApplicationInfo info = getApplicationInfo();
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, info.uid, getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private boolean batteryReliabilityEnabled() {
        if (Build.VERSION.SDK_INT < 23) return true;
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        return power != null && power.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestBatteryReliability() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception error) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void maybeExplainBatteryReliability() {
        if (isFinishing() || batteryReliabilityEnabled() || !LockStore.isEnabled(this)
                || LockStore.packages(this).isEmpty()) return;
        SharedPreferences prompts = getSharedPreferences("focuslock_reliability", MODE_PRIVATE);
        if (prompts.getBoolean("battery_prompt_v101", false)) return;
        prompts.edit().putBoolean("battery_prompt_v101", true).apply();
        new AlertDialog.Builder(this)
                .setTitle("Keep your boundary active")
                .setMessage("Android may quietly stop FocusLock after a few hours to save battery. Tap Allow on the next Android screen so your selected-app boundary can keep running. This does not make other apps slower.")
                .setPositiveButton("Allow", (dialog, which) -> requestBatteryReliability())
                .setNegativeButton("Later", null)
                .show();
    }

    private void maybeShowSelfLockGuide() {
        if (isFinishing() || selfLockCard == null || lockFocusLockCheck == null) return;
        SharedPreferences onboarding = getSharedPreferences("focuslock_onboarding", MODE_PRIVATE);
        if (!onboarding.getBoolean("guide_complete", false)
                || onboarding.getBoolean("self_lock_guide_seen", false)
                || LockStore.lockFocusLock(this)
                || LockStore.isLocked(this, getPackageName())) return;
        onboarding.edit().putBoolean("self_lock_guide_seen", true).apply();
        new AlertDialog.Builder(this)
                .setTitle("Lock FocusLock too?")
                .setMessage("When a selected app is paused, this option also keeps FocusLock unavailable until that pause ends. You can change it anytime just below Protection.")
                .setPositiveButton("Turn on", (dialog, which) -> {
                    lockFocusLockCheck.setChecked(true);
                    markDirty();
                    pulseTarget(selfLockCard);
                    toast("Tap Save & start to apply this option.");
                })
                .setNegativeButton("Skip for now", (dialog, which) -> {
                    lockFocusLockCheck.setChecked(false);
                    toast("FocusLock will stay available during pauses.");
                })
                .show();
    }

    private long parseDuration(EditText minutesInput, EditText secondsInput, String label) {
        try {
            int minutes = Integer.parseInt(minutesInput.getText().toString().trim());
            int seconds = Integer.parseInt(secondsInput.getText().toString().trim());
            if (minutes >= 0 && seconds >= 0 && seconds <= 59 && (minutes > 0 || seconds > 0)) return (minutes * 60L + seconds) * 1000L;
        } catch (Exception ignored) {}
        toast("Enter a " + label + " above zero, with seconds between 0 and 59.");
        return -1;
    }

    private void scrollToBoundarySetup() {
        if (mainScroll == null || appSectionAnchor == null) return;
        updateGuideStep(2, appSectionAnchor);
    }

    private void maybeGuideToTime() {
        if (getSharedPreferences("focuslock_onboarding", MODE_PRIVATE).getBoolean("guide_complete", false)) return;
        if (currentGuideStep >= 3 || selectedAppCount() == 0) return;
        guideHandler.postDelayed(() -> {
            if (selectedAppCount() > 0 && currentGuideStep < 3) updateGuideStep(3, settingsAnchor);
        }, 420);
    }

    private void advanceTimerGuide(boolean useTimer) {
        if (getSharedPreferences("focuslock_onboarding", MODE_PRIVATE).getBoolean("guide_complete", false)) return;
        if (selectedAppCount() == 0) return;
        int expectedStep = useTimer ? 3 : 4;
        if (currentGuideStep != expectedStep) return;
        guideHandler.postDelayed(() -> {
            if (currentGuideStep != expectedStep) return;
            if (useTimer && readDurationSilently(graceInput, graceSecondsInput) > 0) {
                updateGuideStep(4, guideLockTimerTarget);
            } else if (!useTimer && readDurationSilently(durationInput, durationSecondsInput) > 0) {
                updateGuideStep(5, saveButton);
            }
        }, 520);
    }

    private void resumeGuide() {
        if (guideCard == null || !getSharedPreferences("focuslock_onboarding", MODE_PRIVATE)
                .getBoolean("welcome_seen", false)) return;
        if (getSharedPreferences("focuslock_onboarding", MODE_PRIVATE).getBoolean("guide_complete", false)) {
            guideCard.setVisibility(View.GONE);
            return;
        }
        boolean permissionsReady = usageAccessEnabled() && Settings.canDrawOverlays(this)
                && batteryReliabilityEnabled();
        if (!permissionsReady) updateGuideStep(1, permissionSectionAnchor);
        else if (newGuideIntro || selectedAppCount() == 0) {
            newGuideIntro = false;
            updateGuideStep(2, appSectionAnchor);
        }
        else updateGuideStep(3, settingsAnchor);
    }

    private void restartGuide() {
        getSharedPreferences("focuslock_onboarding", MODE_PRIVATE).edit()
                .putBoolean("guide_complete", false)
                .remove("app_tip_seen")
                .remove("time_tip_seen")
                .remove("finish_seen")
                .apply();
        currentGuideStep = 0;
        if (!usageAccessEnabled() || !Settings.canDrawOverlays(this) || !batteryReliabilityEnabled()) {
            updateGuideStep(1, permissionSectionAnchor);
        } else {
            updateGuideStep(2, appSectionAnchor);
        }
    }

    private void advanceGuideManually() {
        if (currentGuideStep == 1) {
            startEasySetup();
        } else if (currentGuideStep == 2 && selectedAppCount() > 0) {
            updateGuideStep(3, settingsAnchor);
        } else if (currentGuideStep == 3) {
            showTimerWheel(true);
        } else if (currentGuideStep == 4) {
            showTimerWheel(false);
        } else if (currentGuideStep == 5) {
            startCommitment();
        }
    }

    private void updateGuideStep(int step, View target) {
        if (guideCard == null || target == null || contentRoot == null) return;
        if (step == 2 && guideAppTarget != null) target = guideAppTarget;
        if (step == 3 && guideTimerTarget != null) target = guideTimerTarget;
        if (step == 4 && guideLockTimerTarget != null) target = guideLockTimerTarget;
        currentGuideStep = step;
        guideCard.setVisibility(View.GONE);
        if (step == 1) {
            guideTitle.setText("1 / 5");
            guideBody.setText("Allow required access");
        } else if (step == 2) {
            guideTitle.setText("2 / 5");
            guideBody.setText(guideAppTarget == null
                    ? "Tap an app to select it"
                    : "Tap " + guideAppTarget.getContentDescription() + " to select it");
        } else if (step == 3) {
            guideTitle.setText("3 / 5");
            guideBody.setText("Tap Scroll to set exact time");
        } else if (step == 4) {
            guideTitle.setText("4 / 5");
            guideBody.setText("Tap Scroll to set lock length");
        } else if (step == 5) {
            guideTitle.setText("5 / 5");
            guideBody.setText("Tap Save & start");
        } else {
            guideTitle.setText("DONE");
            guideBody.setText("FocusLock is active");
        }
        if (step < 6) showCoachStep(step, target, guideBody.getText().toString());
        else pulseTarget(target);
    }

    private void showCoachStep(int step, View target, String message) {
        if (screenRoot == null || mainScroll == null || target == null || isFinishing()) return;
        if (coachOverlay != null) screenRoot.removeView(coachOverlay);
        int[] targetLocation = new int[2];
        int[] scrollLocation = new int[2];
        target.getLocationOnScreen(targetLocation);
        mainScroll.getLocationOnScreen(scrollLocation);
        if (step != 5) {
            mainScroll.smoothScrollBy(0, targetLocation[1] - scrollLocation[1] - dp(145));
        }
        mainScroll.postDelayed(() -> {
            if (isFinishing() || screenRoot == null || currentGuideStep != step) return;
            Runnable action = null;
            if (step == 1) action = this::startEasySetup;
            else if (step == 2) action = target::performClick;
            else if (step == 3) action = () -> showTimerWheel(true);
            else if (step == 4) action = () -> showTimerWheel(false);
            else if (step == 5) action = this::startCommitment;
            coachOverlay = new CoachMarkOverlay(this, target, message, action);
            screenRoot.addView(coachOverlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }, 430);
    }

    private void pulseTarget(View target) {
        target.animate().cancel();
        target.setAlpha(.55f);
        target.setScaleX(.98f);
        target.setScaleY(.98f);
        target.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(180).setDuration(520).start();
    }

    private long readDurationSilently(EditText minutesInput, EditText secondsInput) {
        if (minutesInput == null || secondsInput == null) return -1;
        try {
            int minutes = Integer.parseInt(minutesInput.getText().toString().trim());
            int seconds = Integer.parseInt(secondsInput.getText().toString().trim());
            if (minutes >= 0 && seconds >= 0 && seconds <= 59 && (minutes > 0 || seconds > 0)) {
                return (minutes * 60L + seconds) * 1000L;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private void reveal(View view, long delay) {
        view.setAlpha(0f);
        view.setTranslationY(dp(18));
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(520).start();
    }

    private void markDirty() {
        if (saveButton == null) return;
        saveButton.setEnabled(true);
        saveButton.setBackground(shape(INK, INK, 24));
        saveButton.setTextColor(Color.WHITE);
        saveButton.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();
        saveButton.setText("Save & start  →");
    }

    private void markSaved() {
        if (saveButton == null) return;
        saveButton.setText(LockStore.isEnabled(this) ? "Protection active  ✓" : "Saved  ✓");
        saveButton.setEnabled(false);
        saveButton.setBackground(shape(SOFT_VIOLET, BORDER, 24));
        saveButton.setTextColor(GREEN);
        saveButton.animate().alpha(.92f).setDuration(320).start();
    }

    private String timerSummaryText(long useMs, long lockMs) {
        return "Use " + friendly(useMs) + "  →  Lock " + friendly(lockMs);
    }

    private LinearLayout stepperTimerCard(String title, String detail, boolean useTimer, long initialMs) {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(13), dp(14), dp(12));
        card.setBackground(shape(Color.rgb(249, 252, 248), BORDER, 20));
        card.setContentDescription(title + " timer");

        LinearLayout heading = row();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = text(useTimer ? "1" : "2", 12, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(useTimer ? GREEN : VIOLET, useTimer ? GREEN : VIOLET, 18));
        heading.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        LinearLayout copy = column();
        copy.addView(text(title, 14, INK, true));
        copy.addView(text(detail, 9, MUTED, false), topMargin(2));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = dp(10);
        heading.addView(copy, copyParams);
        card.addView(heading);

        TextView value = text(timerDigits(initialMs), 27, INK, true);
        value.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        value.setGravity(Gravity.CENTER);
        value.setPadding(dp(8), dp(9), dp(8), 0);
        value.setContentDescription(title + " value " + friendly(initialMs));
        value.setOnClickListener(v -> showTimerWheel(useTimer));
        attachPressAnimation(value);
        card.addView(value, topMargin(7));
        if (useTimer) useTimerValue = value; else lockTimerValue = value;

        TextView units = text("HOUR       MIN       SEC", 9, FAINT, true);
        units.setGravity(Gravity.CENTER);
        units.setPadding(0, 0, 0, dp(8));
        card.addView(units);

        LinearLayout controls = row();
        controls.setGravity(Gravity.CENTER);
        controls.addView(timerAdjustButton("−1m", useTimer, -60), timerControlParams(false));
        controls.addView(timerAdjustButton("−10s", useTimer, -10), timerControlParams(true));
        controls.addView(timerAdjustButton("+10s", useTimer, 10), timerControlParams(true));
        controls.addView(timerAdjustButton("+1m", useTimer, 60), timerControlParams(true));
        card.addView(controls);

        TextView scrollAction = text("↕   Scroll to set exact time", 12, VIOLET, true);
        scrollAction.setGravity(Gravity.CENTER);
        scrollAction.setPadding(dp(10), dp(11), dp(10), dp(11));
        scrollAction.setBackground(shape(Color.WHITE, BORDER, 16));
        scrollAction.setContentDescription("Open " + title + " scroll picker");
        scrollAction.setOnClickListener(v -> showTimerWheel(useTimer));
        attachPressAnimation(scrollAction);
        card.addView(scrollAction, topMargin(8));
        return card;
    }

    private LinearLayout.LayoutParams timerControlParams(boolean margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(43), 1f);
        if (margin) params.leftMargin = dp(6);
        return params;
    }

    private TextView timerAdjustButton(String label, boolean useTimer, int deltaSeconds) {
        TextView control = text(label, 11, deltaSeconds > 0 ? Color.WHITE : VIOLET, true);
        control.setGravity(Gravity.CENTER);
        control.setBackground(shape(deltaSeconds > 0 ? GREEN : SOFT_VIOLET,
                deltaSeconds > 0 ? GREEN : BORDER, 15));
        control.setContentDescription((deltaSeconds > 0 ? "Add " : "Subtract ")
                + friendly(Math.abs(deltaSeconds) * 1000L));
        control.setOnClickListener(v -> adjustTimer(useTimer, deltaSeconds));
        attachPressAnimation(control);
        return control;
    }

    private void adjustTimer(boolean useTimer, int deltaSeconds) {
        EditText minutesInput = useTimer ? graceInput : durationInput;
        EditText secondsInput = useTimer ? graceSecondsInput : durationSecondsInput;
        long currentMs = readDurationSilently(minutesInput, secondsInput);
        int currentSeconds = currentMs > 0 ? (int) (currentMs / 1000L) : 1;
        int maximum = (useTimer ? 12 : 48) * 3600 + 3599;
        int nextSeconds = Math.max(1, Math.min(maximum, currentSeconds + deltaSeconds));
        if (nextSeconds == currentSeconds) return;

        minutesInput.setText(String.valueOf(nextSeconds / 60));
        secondsInput.setText(String.valueOf(nextSeconds % 60));
        TextView value = useTimer ? useTimerValue : lockTimerValue;
        View card = useTimer ? useTimerCard : lockTimerCard;
        if (value != null) animateTimerValue(value, nextSeconds * 1000L, deltaSeconds > 0);
        if (card != null) {
            card.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            card.animate().cancel();
            card.animate().scaleX(1.018f).scaleY(1.018f).setDuration(90)
                    .withEndAction(() -> card.animate().scaleX(1f).scaleY(1f)
                            .setInterpolator(new DecelerateInterpolator()).setDuration(150).start()).start();
        }
        updateTimerSummaryAnimated();
        markDirty();
        advanceTimerGuide(useTimer);
    }

    private void showTimerWheel(boolean useTimer) {
        EditText minutesInput = useTimer ? graceInput : durationInput;
        EditText secondsInput = useTimer ? graceSecondsInput : durationSecondsInput;
        long currentMs = readDurationSilently(minutesInput, secondsInput);
        int currentSeconds = currentMs > 0 ? (int) (currentMs / 1000L) : 60;
        int maxHours = useTimer ? 12 : 48;
        currentSeconds = Math.min(currentSeconds, maxHours * 3600 + 3599);

        LinearLayout panel = column();
        panel.setPadding(dp(20), dp(20), dp(20), dp(12));
        panel.addView(text(useTimer ? "Set use limit" : "Set lock length", 21, INK, true));
        panel.addView(text("Scroll each column. Exact to the second.", 11, MUTED, false), topMargin(3));

        TextView preview = text(friendly(currentSeconds * 1000L), 15, VIOLET, true);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(10), dp(9), dp(10), dp(9));
        preview.setBackground(shape(SOFT_VIOLET, BORDER, 16));
        panel.addView(preview, topMargin(13));

        LinearLayout labels = row();
        String[] names = {"HOURS", "MINUTES", "SECONDS"};
        for (String name : names) {
            TextView label = text(name, 8, FAINT, true);
            label.setGravity(Gravity.CENTER);
            labels.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        panel.addView(labels, topMargin(13));

        NumberPicker hours = scrollPicker(0, maxHours, currentSeconds / 3600);
        NumberPicker minutes = scrollPicker(0, 59, (currentSeconds % 3600) / 60);
        NumberPicker seconds = scrollPicker(0, 59, currentSeconds % 60);

        FrameLayout wheelStage = new FrameLayout(this);
        wheelStage.setBackground(shape(Color.rgb(249, 252, 248), BORDER, 20));
        View selectionBand = new View(this);
        selectionBand.setBackground(shape(Color.rgb(226, 243, 226), Color.rgb(182, 215, 187), 14));
        FrameLayout.LayoutParams bandParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46), Gravity.CENTER);
        bandParams.leftMargin = dp(7);
        bandParams.rightMargin = dp(7);
        wheelStage.addView(selectionBand, bandParams);

        LinearLayout wheels = row();
        wheels.setGravity(Gravity.CENTER);
        wheels.addView(hours, new LinearLayout.LayoutParams(0, dp(138), 1f));
        wheels.addView(minutes, new LinearLayout.LayoutParams(0, dp(138), 1f));
        wheels.addView(seconds, new LinearLayout.LayoutParams(0, dp(138), 1f));
        wheelStage.addView(wheels, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(138), Gravity.CENTER));
        panel.addView(wheelStage, topMargin(5));

        NumberPicker.OnValueChangeListener listener = (picker, oldValue, newValue) -> {
            picker.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            int total = hours.getValue() * 3600 + minutes.getValue() * 60 + seconds.getValue();
            animateWheelPreview(preview, Math.max(1, total) * 1000L, newValue >= oldValue);
        };
        hours.setOnValueChangedListener(listener);
        minutes.setOnValueChangedListener(listener);
        seconds.setOnValueChangedListener(listener);

        ObjectAnimator bandPulse = ObjectAnimator.ofFloat(selectionBand, "alpha", .62f, 1f);
        bandPulse.setDuration(1050);
        bandPulse.setRepeatCount(ObjectAnimator.INFINITE);
        bandPulse.setRepeatMode(ObjectAnimator.REVERSE);
        bandPulse.setInterpolator(new AccelerateDecelerateInterpolator());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(panel)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply time", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            bandPulse.start();
            panel.setAlpha(0f);
            panel.setTranslationY(dp(18));
            panel.animate().alpha(1f).translationY(0f)
                    .setInterpolator(new DecelerateInterpolator()).setDuration(260).start();
            Button apply = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            apply.setTextColor(GREEN);
            attachPressAnimation(apply);
            apply.setOnClickListener(v -> {
                int nextSeconds = hours.getValue() * 3600 + minutes.getValue() * 60 + seconds.getValue();
                if (nextSeconds <= 0) {
                    preview.setText("Choose at least 1 second");
                    preview.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    preview.animate().translationX(dp(7)).setDuration(55)
                            .withEndAction(() -> preview.animate().translationX(-dp(7)).setDuration(55)
                                    .withEndAction(() -> preview.animate().translationX(0f).setDuration(55).start()).start()).start();
                    return;
                }
                applyWheelTime(useTimer, nextSeconds);
                dialog.dismiss();
            });
        });
        dialog.setOnDismissListener(ignored -> bandPulse.cancel());
        dialog.show();
    }

    private NumberPicker scrollPicker(int min, int max, int value) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(Math.max(min, Math.min(max, value)));
        picker.setFormatter(number -> String.format(java.util.Locale.US, "%02d", number));
        picker.setWrapSelectorWheel(true);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        picker.setOnLongPressUpdateInterval(70);
        picker.setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            picker.setTextColor(INK);
            picker.setTextSize(dp(20));
        }
        return picker;
    }

    private void animateWheelPreview(TextView preview, long nextMs, boolean increasing) {
        preview.animate().cancel();
        preview.animate().alpha(.25f).translationY(increasing ? -dp(4) : dp(4)).setDuration(55)
                .withEndAction(() -> {
                    preview.setText(friendly(nextMs));
                    preview.setTranslationY(increasing ? dp(5) : -dp(5));
                    preview.animate().alpha(1f).translationY(0f).setDuration(110).start();
                }).start();
    }

    private void applyWheelTime(boolean useTimer, int nextSeconds) {
        EditText minutesInput = useTimer ? graceInput : durationInput;
        EditText secondsInput = useTimer ? graceSecondsInput : durationSecondsInput;
        long previousMs = readDurationSilently(minutesInput, secondsInput);
        minutesInput.setText(String.valueOf(nextSeconds / 60));
        secondsInput.setText(String.valueOf(nextSeconds % 60));
        TextView value = useTimer ? useTimerValue : lockTimerValue;
        View card = useTimer ? useTimerCard : lockTimerCard;
        if (value != null) animateTimerValue(value, nextSeconds * 1000L, nextSeconds * 1000L >= previousMs);
        if (card != null) {
            card.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            card.setScaleX(.97f);
            card.setScaleY(.97f);
            card.animate().scaleX(1.025f).scaleY(1.025f).setDuration(150)
                    .withEndAction(() -> card.animate().scaleX(1f).scaleY(1f).setDuration(180).start()).start();
        }
        updateTimerSummaryAnimated();
        markDirty();
        advanceTimerGuide(useTimer);
    }

    private void animateTimerValue(TextView value, long nextMs, boolean increasing) {
        value.animate().cancel();
        value.animate().alpha(.15f).translationY(increasing ? -dp(9) : dp(9)).setDuration(75)
                .withEndAction(() -> {
                    value.setText(timerDigits(nextMs));
                    value.setContentDescription("Timer value " + friendly(nextMs));
                    value.setTranslationY(increasing ? dp(10) : -dp(10));
                    value.animate().alpha(1f).translationY(0f)
                            .setInterpolator(new DecelerateInterpolator()).setDuration(150).start();
                }).start();
    }

    private void updateTimerSummaryAnimated() {
        if (timerSummary == null) return;
        long useMs = readDurationSilently(graceInput, graceSecondsInput);
        long lockMs = readDurationSilently(durationInput, durationSecondsInput);
        timerSummary.animate().cancel();
        timerSummary.animate().alpha(.2f).translationY(-dp(3)).setDuration(70)
                .withEndAction(() -> {
                    timerSummary.setText(timerSummaryText(useMs, lockMs));
                    timerSummary.setTranslationY(dp(3));
                    timerSummary.animate().alpha(1f).translationY(0f).setDuration(140).start();
                }).start();
    }

    private void updateTimerSteppers(long useMs, long lockMs) {
        if (useTimerValue != null) useTimerValue.setText(timerDigits(useMs));
        if (lockTimerValue != null) lockTimerValue.setText(timerDigits(lockMs));
        if (timerSummary != null) timerSummary.setText(timerSummaryText(useMs, lockMs));
    }

    private String timerDigits(long milliseconds) {
        long totalSeconds = Math.max(1, milliseconds / 1000L);
        return String.format(java.util.Locale.US, "%02d : %02d : %02d",
                totalSeconds / 3600L, (totalSeconds % 3600L) / 60L, totalSeconds % 60L);
    }

    private EditText numberInput(String value) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setTextSize(24);
        input.setTextColor(INK);
        input.setGravity(Gravity.CENTER);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setPadding(dp(8), dp(7), dp(8), dp(7));
        input.setBackground(shape(Color.rgb(249, 250, 251), BORDER, 14));
        if (Build.VERSION.SDK_INT >= 21) input.setBackgroundTintList(null);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                markDirty();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        return input;
    }


    private Button button(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(11);
        button.setTextColor(foreground);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackground(shape(background, background, 22));
        if (Build.VERSION.SDK_INT >= 21) button.setBackgroundTintList(null);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams topMargin(int margin) { LinearLayout.LayoutParams p = matchWrap(); p.topMargin = dp(margin); return p; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String friendly(long ms) {
        long totalSeconds = Math.max(0, ms / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder value = new StringBuilder();
        if (hours > 0) value.append(hours).append("h");
        if (minutes > 0) {
            if (value.length() > 0) value.append(' ');
            value.append(minutes).append("m");
        }
        if (seconds > 0 || value.length() == 0) {
            if (value.length() > 0) value.append(' ');
            value.append(seconds).append("s");
        }
        return value.toString();
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
