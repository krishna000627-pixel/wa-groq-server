package com.aria.reply;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SecurePrefs {
    private static final String KS    = "AndroidKeyStore";
    private static final String ALIAS = "aria_v30_key";
    private final SharedPreferences p;

    public SecurePrefs(Context c) {
        p = c.getSharedPreferences("aria_secure_v30", Context.MODE_PRIVATE);
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(KS);
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS);
            g.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
            g.generateKey();
        }
        java.security.Key k = ks.getKey(ALIAS, null);
        return k instanceof SecretKey ? (SecretKey) k : null;
    }

    public void put(String k, String value) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            String v = Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":"
                     + Base64.encodeToString(
                           c.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
            p.edit().putString(k, v).apply();
        } catch (Exception ignored) {}
    }

    public String get(String k) {
        try {
            String v = p.getString(k, null);
            if (v == null) return "";
            String[] x  = v.split(":", 2);
            byte[]   iv  = Base64.decode(x[0], Base64.NO_WRAP);
            byte[]   data = Base64.decode(x[1], Base64.NO_WRAP);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(data), StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }
}
