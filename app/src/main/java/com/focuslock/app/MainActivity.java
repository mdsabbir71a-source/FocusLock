package com.focuslock.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.animation.AnimatorSet;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static volatile boolean visible;
    private static final int INK = Color.rgb(17, 24, 39);
    private static final int MUTED = Color.rgb(107, 114, …29400 tokens truncated…ad"><p class="brand">FOCUSLOCK</p><h1>Your account</h1><p>Manage your FocusLock experience.</p></header><section class="scroll"><div class="profile"><div class="avatar">F</div><div><strong>FocusLock user</strong><span>Signed in securely</span></div></div><div class="section"><p class="label">MEMBERSHIP</p><div class="plan"><small>FOCUSLOCK FREE</small><b>Your focus is protected</b><span>Upgrade options will appear here when available.</span></div></div><div class="section"><p class="label">ACCOUNT</p><div class="group"><button class="row" data-action="details"><div class="ico">✦</div><div class="copy"><b>Personal details</b><span>Name and email</span></div><div class="chev">›</div></button><button class="row" data-action="notifications"><div class="ico">◌</div><div class="copy"><b>Notifications</b><span>Gentle focus reminders</span></div><div class="chev">›</div></button><button class="row" data-action="support"><div class="ico">?</div><div class="copy"><b>Help & support</b><span>Guides and contact</span></div><div class="chev">›</div></button></div></div><div class="section"><p class="label">SECURITY</p><div class="group"><button class="row" data-action="password"><div class="ico">⌁</div><div class="copy"><b>Change password</b><span>Keep your account secure</span></div><div class="chev">›</div></button><button class="row" data-action="signout"><div class="ico">↪</div><div class="copy"><b>Sign out</b><span>Use a different account</span></div><div class="chev">›</div></button></div></div></section><nav class="nav"><span><svg viewBox="0 0 24 24"><path d="M3 11l9-8 9 8M5 10v10h14V10"/></svg>Home</span><span><svg viewBox="0 0 24 24"><path d="M6 20V11M12 20V4M18 20v-6"/></svg>Analytics</span><span class="active"><svg viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"/><path d="M4 21c1-4 4-6 8-6s7 2 8 6"/></svg>Account</span></nav></main></body>
</html>
