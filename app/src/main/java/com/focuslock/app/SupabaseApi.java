package com.focuslock.app;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal Supabase Auth/PostgREST client. Only the publishable client key is embedded. */
public final class SupabaseApi {
    public interface Callback<T> { void complete(T value, String error); }

    public static final class AuthResult {
        public final boolean signedIn;
        public final boolean needsEmailConfirmation;
        AuthResult(boolean signedIn, boolean needsEmailConfirmation) {
            this.signedIn = signedIn;
            this.needsEmailConfirmation = needsEmailConfirmation;
        }
    }

    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /**
     * Supabase rotates refresh tokens.  Serialising refreshes prevents two
     * simultaneous API calls (for example entitlement and remote config on
     * launch) from spending the same refresh token and invalidating the
     * session used by the other call.
     */
    private static final Object SESSION_LOCK = new Object();
    private static final long SESSION_REFRESH_WINDOW_MS = 60_000L;
    private static final String BASE = BuildConfig.SUPABASE_URL;
    private static final String KEY = BuildConfig.SUPABASE_PUBLISHABLE_KEY;

    private SupabaseApi() {}

    public static void signIn(Context context, String email, String password, Callback<AuthResult> callback) {
        IO.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("email", email).put("password", password);
                Response response = request("POST", "/auth/v1/token?grant_type=password", body.toString(), null, null);
                if (!response.ok()) throw new ApiException(errorMessage(response));
                saveAuthResponse(context, new JSONObject(response.body));
                deliver(callback, new AuthResult(true, false), null);
            } catch (Exception e) { deliver(callback, null, friendly(e)); }
        });
    }

    public static void signUp(Context context, String email, String password, Callback<AuthResult> callback) {
        IO.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("email", email).put("password", password);
                Response response = request("POST", "/auth/v1/signup", body.toString(), null, null);
                if (!response.ok()) throw new ApiException(errorMessage(response));
                JSONObject json = new JSONObject(response.body);
                if (!json.optString("access_token", "").isEmpty()) {
                    saveAuthResponse(context, json);
                    deliver(callback, new AuthResult(true, false), null);
                } else {
                    deliver(callback, new AuthResult(false, true), null);
                }
            } catch (Exception e) { deliver(callback, null, friendly(e)); }
        });
    }

    public static void requestPasswordReset(String email, Callback<Boolean> callback) {
        IO.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("email", email);
                String redirect = URLEncoder.encode("focuslock://auth/callback?type=recovery", "UTF-8");
                Response response = request("POST", "/auth/v1/recover?redirect_to=" + redirect, body.toString(), null, null);
                if (!response.ok()) throw new ApiException(errorMessage(response));
                deliver(callback, true, null);
            } catch (Exception e) { deliver(callback, false, friendly(e)); }
        });
    }

    public static void updatePassword(Context context, String newPassword, Callback<Boolean> callback) {
        IO.execute(() -> {
            try {
                SecureSessionStore.Session session = freshSession(context);
                Response response = requestWithSession(context, "PUT", "/auth/v1/user",
                        new JSONObject().put("password", newPassword).toString(), session, null);
                if (!response.ok()) throw new ApiException(errorMessage(response));
                deliver(callback, true, null);
            } catch (Exception e) { deliver(callback, false, friendly(e)); }
        });
    }

    public static void updateEmail(Context context, String newEmail, Callback<Boolean> callback) {
        IO.execute(() -> {
            try {
                SecureSessionStore.Session session = freshSession(context);
                Response response = requestWithSession(context, "PUT", "/auth/v1/user",
                        new JSONObject().put("email", newEmail).toString(), session, null);
                if (!response.ok()) throw new ApiException(errorMessage(response));
                deliver(callback, true, null);
            } catch (Exception e) { deliver(callback, false, friendly(e)); }
        });
    }

    public static void deleteAccount(Context context, Callback<Boolean> callback) {
        IO.execute(() -> {
            try {
                SecureSessionStore.Session session = freshSession(context);
                Response response = requestWithSession(context, "POST", "/functions/v1/delete-account", "{}", session, null);
                if (!response.ok()) throw new ApiException(errorMessage(response));
                deliver(callback, true, null);
            } catch (Exception e) { deliver(callback, false, friendly(e)); }
        });
    }

    public static void recordConsent(Context context, String documentVersion, Callback<Boolean> callback) {
        IO.execute(() -> {
            try {
                SecureSessionStore.Session session = freshSession(context);
                JSONObject body = new JSONObject()
                        .put("user_id", session.userId)
                        .put("document_version", documentVersion)
                        .put("platform", "android")
                        .put("app_version", BuildConfig.VERSION_NAME);
                Response response = requestWithSession(context, "POST", "/rest/v1/user_consents?on_conflict=user_id,document_version",
                        body.toString(), session, "resolution=merge-duplicates,return=minimal");
                if (!response.ok()) throw new ApiException(errorMessage(response));
                deliver(callback, true, null);
            } catch (Exception e) { deliver(callback, false, friendly(e)); }
        });
    }

    public static void exchangePkce(Context context, String authCode, String verifier, Callback<AuthResult> callback) {
        IO.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("auth_code", authCode).put("code_verifier", verifier);
                Response response = request("POST", "/auth/v1/token?grant_type=pkce", body.toString(), null, null);
                if (!response.ok()) throw new ApiException(errorMessage(response));
                saveAuthResponse(context, new JSONObject(response.body));
                deliver(callback, new AuthResult(true, false), null);
            } catch (Exception e) { deliver(callback, null, friendly(e)); }
        });
    }

    public static void importSessionTokens(Context context, String accessToken, String refreshToken,
                                           long expiresIn, Callback<AuthResult> callback) {
        final String rawAccessToken = accessToken;
        final String rawRefreshToken = refreshToken;
        IO.execute(() -> {
            try {
                String normalizedAccessToken = normalizeToken(rawAccessToken);
                String normalizedRefreshToken = normalizeToken(rawRefreshToken);
                if (normalizedRefreshToken.isEmpty()) throw new ApiException("The server did not return a refresh token.");
                String userId = jwtSubject(normalizedAccessToken);
                AccountStore.save(context, jwtClaim(normalizedAccessToken, "email"), "google");
                long expiresAt = tokenExpiry(normalizedAccessToken, expiresIn);
                SecureSessionStore.save(context, normalizedAccessToken, normalizedRefreshToken, userId, expiresAt);
                deliver(callback, new AuthResult(true, false), null);
            } catch (Exception e) { deliver(callback, null, friendly(e)); }
        });
    }

    public static void refreshEntitlement(Context context, Callback<Boolean> callback) {
        IO.execute(() -> {
            try {
                SecureSessionStore.Session session = freshSession(context);
                String path = "/rest/v1/entitlements?select=status,access_level,valid_until,grace_until,reason"
                        + "&user_id=eq." + encode(session.userId);
                Response response = requestWithSession(context, "GET", path, null, session, null);
                if (!response.ok()) throw new ApiException(errorMessage(response));
                JSONArray rows = new JSONArray(response.body);
                if (rows.length() != 1) throw new ApiException("Account access record was not found.");
                JSONObject row = rows.getJSONObject(0);
                String status = row.optString("status", "revoked");
                String validUntil = nullableString(row, "valid_until");
                String graceUntil = nullableString(row, "grace_until");
                boolean withinAccessTime = validUntil == null || isFuture(validUntil) || isFuture(graceUntil);
                boolean allowed = "active".equals(status) && withinAccessTime;
                if (allowed) AccessStore.allow(context, row.optString("access_level", "free"));
                else AccessStore.deny(context, row.optString("reason", "This account is " + status + "."));
                if (allowed) syncAccountData(context, session);
                deliver(callback, allowed, null);
            } catch (Exception e) {
                if (isAuthFailure(e)) {
                    SecureSessionStore.clear(context);
                    deliver(callback, false, "Your session expired. Please sign in again.");
                } else if (AccessStore.isAllowed(context)) deliver(callback, true, null);
                else deliver(callback, false, friendly(e));
            }
        });
    }

    /**
     * Sends a fresh device snapshot without changing the user's access state.
     * This is used after Android settings screens return and by the active
     * monitor so the owner dashboard never has to guess a permission status.
     */
    public static void syncDeviceState(Context context) {
        IO.execute(() -> {
            try {
                SecureSessionStore.Session session = freshSession(context);
                syncAccountData(context, session);
            } catch (Exception ignored) {
                // Device telemetry must never interrupt FocusLock protection.
            }
        });
    }

    public static void refreshRemoteConfig(Context context, Callback<Boolean> callback) {
        IO.execute(() -> {
            try {
                SecureSessionStore.Session session = freshSession(context);
                Response response = requestWithSession(context, "GET", "/rest/v1/app_remote_config?select=*&id=eq.global", null, session, null);
                if (!response.ok()) throw new ApiException(errorMessage(response));
                JSONArray rows = new JSONArray(response.body);
                if (rows.length() != 1) throw new ApiException("Live app settings were not found.");
                RemoteConfigStore.save(context, rows.getJSONObject(0));
                deliver(callback, true, null);
            } catch (Exception e) {
                if (isAuthFailure(e)) SecureSessionStore.clear(context);
                deliver(callback, false, friendly(e));
            }
        });
    }

    public static void logout(Context context) {
        SecureSessionStore.Session session = SecureSessionStore.get(context);
        SecureSessionStore.clear(context);
        AccountStore.clear(context);
        if (session != null) IO.execute(() -> {
            try { request("POST", "/auth/v1/logout", "{}", session.accessToken, null); }
            catch (Exception ignored) {}
        });
    }

    private static SecureSessionStore.Session freshSession(Context context) throws Exception {
        return freshSession(context, false, null);
    }

    /** Gets a usable session, refreshing at most once when several calls arrive together. */
    private static SecureSessionStore.Session freshSession(Context context, boolean forceRefresh,
                                                           SecureSessionStore.Session observed) throws Exception {
        SecureSessionStore.Session current = SecureSessionStore.get(context);
        if (current == null) throw new ApiException("Please sign in again.");
        if (!forceRefresh && !needsRefresh(current)) return current;

        synchronized (SESSION_LOCK) {
            SecureSessionStore.Session latest = SecureSessionStore.get(context);
            if (latest == null) throw new ApiException("Please sign in again.");
            // Another request may have refreshed while this request was in
            // flight. Reuse that newer token instead of rotating again.
            if ((!forceRefresh && !needsRefresh(latest))
                    || (forceRefresh && observed != null
                    && !latest.accessToken.equals(observed.accessToken)
                    && !needsRefresh(latest))) {
                return latest;
            }
            String refreshToken = normalizeToken(latest.refreshToken);
            if (refreshToken.isEmpty()) {
                SecureSessionStore.clear(context);
                throw new ApiException("Your session expired. Please sign in again.");
            }
            JSONObject body = new JSONObject().put("refresh_token", refreshToken);
            Response response = request("POST", "/auth/v1/token?grant_type=refresh_token",
                    body.toString(), null, null);
            if (!response.ok()) {
                String message = errorMessage(response);
                if (response.code == 400 || response.code == 401 || isAuthFailureMessage(message)) {
                    SecureSessionStore.clear(context);
                    throw new ApiException("Your session expired. Please sign in again.");
                }
                throw new ApiException("Could not refresh your session. Try again shortly.");
            }
            try {
                saveAuthResponse(context, new JSONObject(response.body));
            } catch (Exception invalidSession) {
                SecureSessionStore.clear(context);
                throw new ApiException("The server returned an invalid session. Please sign in again.");
            }
            SecureSessionStore.Session refreshed = SecureSessionStore.get(context);
            if (refreshed == null) throw new ApiException("Could not refresh your session.");
            return refreshed;
        }
    }

    private static boolean needsRefresh(SecureSessionStore.Session session) {
        return session.expiresAt <= System.currentTimeMillis() + SESSION_REFRESH_WINDOW_MS;
    }

    private static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        ApplicationInfo info = context.getApplicationInfo();
        return appOps != null && appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                info.uid, context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    private static void syncAccountData(Context context, SecureSessionStore.Session session) {
        try {
            String installId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (installId == null || installId.length() < 16) installId = "focuslock-install-" + session.userId;
            boolean notificationsGranted = Build.VERSION.SDK_INT < 33
                    || context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            JSONObject device = new JSONObject()
                    .put("user_id", session.userId)
                    .put("install_id", installId)
                    .put("device_name", Build.MANUFACTURER + " " + Build.MODEL)
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL)
                    .put("platform", "android")
                    .put("app_version", BuildConfig.VERSION_NAME)
                    .put("app_version_code", BuildConfig.VERSION_CODE)
                    .put("android_version", Build.VERSION.RELEASE)
                    .put("sdk_int", Build.VERSION.SDK_INT)
                    .put("usage_access_granted", hasUsageAccess(context))
                    .put("overlay_granted", Settings.canDrawOverlays(context))
                    .put("battery_optimization_ignored", power != null && power.isIgnoringBatteryOptimizations(context.getPackageName()))
                    .put("notifications_granted", notificationsGranted)
                    .put("app_enabled", LockStore.isEnabled(context))
                    .put("capabilities_updated_at", Instant.now().toString())
                    .put("last_seen_at", Instant.now().toString());

            // PostgREST upserts with a composite conflict key failed silently on
            // some installed builds. Resolve the row first, then create or update
            // it explicitly so every signed-in device is visible to the owner.
            String lookup = "/rest/v1/devices?select=id&user_id=eq." + encode(session.userId)
                    + "&install_id=eq." + encode(installId) + "&limit=1";
            Response existing = requestWithSession(context, "GET", lookup, null, session, null);
            JSONArray rows = existing.ok() ? new JSONArray(existing.body) : new JSONArray();
            if (rows.length() == 0) {
                requestWithSession(context, "POST", "/rest/v1/devices", device.toString(), session, "return=minimal");
            } else {
                long deviceId = rows.getJSONObject(0).optLong("id", 0);
                if (deviceId > 0) requestWithSession(context, "PATCH", "/rest/v1/devices?id=eq." + deviceId,
                        device.toString(), session, "return=minimal");
            }

            requestWithSession(context, "PATCH", "/rest/v1/profiles?user_id=eq." + encode(session.userId),
                    new JSONObject().put("last_seen_at", Instant.now().toString()).toString(), session,
                    "return=minimal");

            DiagnosticStore.Event event = DiagnosticStore.pending(context);
            if (event != null) {
                JSONObject diagnostic = new JSONObject()
                        .put("user_id", session.userId)
                        .put("event_code", event.code)
                        .put("detail", event.detail)
                        .put("app_version", BuildConfig.VERSION_NAME)
                        .put("android_version", String.valueOf(Build.VERSION.SDK_INT))
                        .put("device_model", Build.MANUFACTURER + " " + Build.MODEL)
                        .put("occurred_at", Instant.ofEpochMilli(event.occurredAt).toString());
                Response diagnosticResponse = requestWithSession(context, "POST", "/rest/v1/app_diagnostics", diagnostic.toString(), session,
                        "return=minimal");
                if (diagnosticResponse.ok()) DiagnosticStore.clearPending(context);
            }
        } catch (Exception ignored) {}
    }

    private static void saveAuthResponse(Context context, JSONObject json) throws Exception {
        String access = normalizeToken(json.optString("access_token", ""));
        String refresh = normalizeToken(json.optString("refresh_token", ""));
        if (access.isEmpty() || refresh.isEmpty()) throw new ApiException("The server did not return a complete session.");
        String userId = "";
        JSONObject user = json.optJSONObject("user");
        if (user != null) {
            userId = user.optString("id", "");
            JSONObject metadata = user.optJSONObject("app_metadata");
            String provider = metadata == null ? "" : metadata.optString("provider", "");
            AccountStore.save(context, user.optString("email", ""), provider);
        }
        if (userId.isEmpty()) userId = jwtSubject(access);
        long expiresAt = tokenExpiry(access, json.optLong("expires_in", 3600));
        SecureSessionStore.save(context, access, refresh, userId, expiresAt);
    }

    private static String jwtSubject(String token) throws Exception {
        String sub = jwtClaim(token, "sub");
        if (sub.isEmpty()) throw new ApiException("Login token has no user ID.");
        return sub;
    }

    private static String jwtClaim(String token, String claim) throws Exception {
        String normalized = normalizeToken(token);
        String[] parts = normalized.split("\\.", -1);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty())
            throw new ApiException("Invalid login token.");
        final byte[] decoded;
        try {
            decoded = Base64.decode(withBase64Padding(parts[1]), Base64.URL_SAFE | Base64.NO_WRAP);
        } catch (IllegalArgumentException invalidBase64) {
            throw new ApiException("Invalid login token.");
        }
        try {
            String payload = new String(decoded, StandardCharsets.UTF_8);
            return new JSONObject(payload).optString(claim, "");
        } catch (Exception invalidPayload) {
            throw new ApiException("Invalid login token payload.");
        }
    }

    private static long tokenExpiry(String accessToken, long expiresIn) {
        long now = System.currentTimeMillis();
        try {
            long exp = jwtLongClaim(accessToken, "exp");
            long expiry = Math.multiplyExact(exp, 1000L);
            // Ignore absurd values while still honoring the server-signed exp
            // claim for normal Supabase sessions. A short token is allowed so
            // freshSession() can immediately rotate it safely.
            if (expiry > 0L && expiry < now + 10L * 365L * 24L * 60L * 60L * 1000L)
                return expiry;
        } catch (Exception ignored) {}
        long seconds = expiresIn <= 0 ? 3600L : Math.max(60L, expiresIn);
        if (seconds > 10L * 365L * 24L * 60L * 60L) seconds = 10L * 365L * 24L * 60L * 60L;
        return now + seconds * 1000L;
    }

    private static long jwtLongClaim(String token, String claim) throws Exception {
        String normalized = normalizeToken(token);
        String[] parts = normalized.split("\\.", -1);
        if (parts.length != 3 || parts[1].isEmpty()) throw new ApiException("Invalid login token.");
        byte[] decoded = Base64.decode(withBase64Padding(parts[1]), Base64.URL_SAFE | Base64.NO_WRAP);
        String value = new JSONObject(new String(decoded, StandardCharsets.UTF_8)).optString(claim, "");
        if (value.isEmpty()) throw new ApiException("Login token has no expiry.");
        return Long.parseLong(value);
    }

    private static String withBase64Padding(String value) throws ApiException {
        int remainder = value.length() % 4;
        if (remainder == 1) throw new ApiException("Invalid login token.");
        if (remainder == 2) return value + "==";
        if (remainder == 3) return value + "=";
        return value;
    }

    private static String normalizeToken(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, 7)) normalized = normalized.substring(7).trim();
        return normalized;
    }

    private static Response requestWithSession(Context context, String method, String path, String json,
                                               SecureSessionStore.Session observed, String prefer) throws Exception {
        Response response = request(method, path, json, observed.accessToken, prefer);
        if (!isAuthResponse(response)) return response;
        SecureSessionStore.Session refreshed = freshSession(context, true, observed);
        Response retry = request(method, path, json, refreshed.accessToken, prefer);
        if (isAuthResponse(retry)) {
            SecureSessionStore.clear(context);
            throw new ApiException("Your session expired. Please sign in again.");
        }
        return retry;
    }

    private static boolean isAuthResponse(Response response) {
        if (response.code == 401) return true;
        return response.code == 403 && isAuthFailureMessage(errorMessage(response));
    }

    private static boolean isAuthFailure(Exception error) {
        return isAuthFailureMessage(error == null ? "" : error.getMessage());
    }

    private static boolean isAuthFailureMessage(String message) {
        if (message == null) return false;
        String value = message.toLowerCase(Locale.US);
        return value.contains("jwt") || value.contains("invalid token")
                || value.contains("token expired") || value.contains("token is expired")
                || value.contains("unauthorized") || value.contains("refresh token")
                || value.contains("session expired") || value.contains("session is invalid");
    }

    private static boolean isFuture(String iso) {
        if (iso == null || iso.isEmpty() || "null".equals(iso)) return false;
        try { return Instant.parse(iso).isAfter(Instant.now()); }
        catch (Exception ignored) { return false; }
    }

    private static String nullableString(JSONObject object, String key) {
        if (object.isNull(key)) return null;
        String value = object.optString(key, null);
        return value == null || value.isEmpty() || "null".equals(value) ? null : value;
    }

    private static Response request(String method, String path, String json, String bearer, String prefer) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(BASE + path).openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(15_000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("apikey", KEY);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        if (bearer != null) connection.setRequestProperty("Authorization", "Bearer " + bearer);
        if (prefer != null) connection.setRequestProperty("Prefer", prefer);
        if (json != null) {
            connection.setDoOutput(true);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 400 ? connection.getInputStream() : connection.getErrorStream();
        StringBuilder body = new StringBuilder();
        if (stream != null) try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        connection.disconnect();
        return new Response(code, body.toString());
    }

    private static String errorMessage(Response response) {
        try {
            JSONObject json = new JSONObject(response.body);
            String value = json.optString("msg", json.optString("message",
                    json.optString("error_description", json.optString("error", ""))));
            if (!value.isEmpty()) return value;
        } catch (Exception ignored) {}
        return "Request failed (" + response.code + ").";
    }

    private static String friendly(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return "Could not connect. Check your internet and try again.";
        if (message.contains("Unable to resolve host") || message.contains("timed out"))
            return "Could not connect. Check your internet and try again.";
        return message;
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static <T> void deliver(Callback<T> callback, T value, String error) {
        MAIN.post(() -> callback.complete(value, error));
    }

    private static final class Response {
        final int code;
        final String body;
        Response(int code, String body) { this.code = code; this.body = body; }
        boolean ok() { return code >= 200 && code < 300; }
    }

    private static final class ApiException extends Exception {
        ApiException(String message) { super(message); }
    }
}
