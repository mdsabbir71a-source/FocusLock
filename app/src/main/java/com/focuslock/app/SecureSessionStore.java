package com.focuslock.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores Supabase session tokens encrypted by a non-exportable Android Keystore key. */
public final class SecureSessionStore {
    private static final String PREFS = "focuslock_secure_session";
    private static final String VALUE = "encrypted_session";
    private static final String KEY_ALIAS = "FocusLockSessionKeyV1";

    private SecureSessionStore() {}

    public static final class Session {
        public final String accessToken;
        public final String refreshToken;
        public final String userId;
        public final long expiresAt;

        Session(String accessToken, String refreshToken, String userId, long expiresAt) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }

    public static void save(Context context, String accessToken, String refreshToken, String userId, long expiresAt) {
        try {
            JSONObject json = new JSONObject();
            json.put("access_token", accessToken);
            json.put("refresh_token", refreshToken);
            json.put("user_id", userId);
            json.put("expires_at", expiresAt);
            String encrypted = encrypt(json.toString());
            prefs(context).edit().putString(VALUE, encrypted).apply();
        } catch (Exception ignored) {
            clear(context);
        }
    }

    public static Session get(Context context) {
        String encrypted = prefs(context).getString(VALUE, null);
        if (encrypted == null) return null;
        try {
            JSONObject json = new JSONObject(decrypt(encrypted));
            String access = json.optString("access_token", "");
            String refresh = json.optString("refresh_token", "");
            String userId = json.optString("user_id", "");
            if (access.isEmpty() || refresh.isEmpty() || userId.isEmpty()) return null;
            return new Session(access, refresh, userId, json.optLong("expires_at", 0));
        } catch (Exception ignored) {
            clear(context);
            return null;
        }
    }

    public static boolean hasSession(Context context) { return get(context) != null; }

    public static void clear(Context context) {
        prefs(context).edit().remove(VALUE).apply();
        AccessStore.clear(context);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) store.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static String encrypt(String plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "."
                + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
    }

    private static String decrypt(String value) throws Exception {
        String[] parts = value.split("\\.", 2);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid encrypted session");
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }
}
