package com.focuslock.app;

import android.content.Context;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;

/** Draft client for a future authenticated and rate-limited verification service. */
final class PlayPurchaseVerifier {
    interface Callback { void onResult(boolean verified, long expiryMillis, String error); }
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void verify(Context context, String purchaseToken, Callback callback) {
        if (purchaseToken == null || purchaseToken.isEmpty()) {
            MAIN.post(() -> callback.onResult(false, 0, "Purchase token missing."));
            return;
        }
        IO.execute(() -> {
            HttpURLConnection connection = null;
            boolean verified = false;
            long expires = 0;
            String error = null;
            try {
                // No endpoint is deployed yet. Keep checkout disabled until this
                // route has authentication, abuse protection and ownership checks.
                URL url = new URL(BuildConfig.SUPABASE_URL + "/functions/v1/verify-play-purchase");
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY);
                byte[] body = new JSONObject().put("purchaseToken", purchaseToken).toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
                if (connection.getResponseCode() != 200) throw new IllegalStateException();
                byte[] response;
                try (InputStream input = connection.getInputStream()) {
                    java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                    byte[] chunk = new byte[2048];
                    int n;
                    while ((n = input.read(chunk)) != -1) {
                        if (buffer.size() + n > 16384) throw new IllegalStateException();
                        buffer.write(chunk, 0, n);
                    }
                    response = buffer.toByteArray();
                }
                JSONObject json = new JSONObject(new String(response, StandardCharsets.UTF_8));
                verified = json.optBoolean("entitled", false);
                String expiry = json.optString("expiresAt", "");
                if (verified) {
                    java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
                    format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    java.util.Date date = format.parse(expiry);
                    expires = date == null ? 0 : date.getTime();
                    verified = expires > System.currentTimeMillis();
                }
                if (!verified) error = "No active subscription was verified.";
            } catch (Exception ignored) {
                verified = false;
                error = "Could not verify your subscription. Please try again.";
            } finally {
                if (connection != null) connection.disconnect();
            }
            final boolean result = verified;
            final long expiry = expires;
            final String message = error;
            MAIN.post(() -> callback.onResult(result, expiry, message));
        });
    }
}
