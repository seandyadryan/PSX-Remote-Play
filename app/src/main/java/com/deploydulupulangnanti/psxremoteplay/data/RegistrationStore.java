package com.deploydulupulangnanti.psxremoteplay.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONException;
import org.json.JSONObject;
import com.deploydulupulangnanti.psxremoteplay.core.ConsoleRegistration;
import com.deploydulupulangnanti.psxremoteplay.core.Device;

/** Pairing results encrypted with an app-UID-bound key; no PIN/account password is persisted. */
public final class RegistrationStore {
    private static final Object KEY_LOCK = new Object();
    private final SharedPreferences preferences;
    private final String keyAlias;
    public RegistrationStore(Context context) {
        preferences = context.getSharedPreferences("psx_registrations_v1", Context.MODE_PRIVATE);
        keyAlias = context.getPackageName() + ".console-registration.v1";
    }
    public boolean has(String deviceId) { return preferences.contains(validId(deviceId)); }
    public synchronized void save(String deviceId, ConsoleRegistration registration) {
        String id = validId(deviceId);
        try {
            JSONObject data = new JSONObject().put("version", 1).put("ps5", registration.ps5)
                    .put("name", registration.name).put("mac", encode(registration.serverMac()))
                    .put("registrationKey", encode(registration.registrationKey())).put("morning", encode(registration.morning()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey());
            cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
            JSONObject envelope = new JSONObject().put("iv", encode(cipher.getIV()))
                    .put("ciphertext", encode(cipher.doFinal(data.toString().getBytes(StandardCharsets.UTF_8))));
            if (!preferences.edit().putString(id, envelope.toString()).commit()) throw new IllegalStateException("Kredensial gagal disimpan.");
        } catch (GeneralSecurityException | JSONException error) {
            throw new IllegalStateException("Penyimpanan pairing aman tidak tersedia.");
        }
    }
    public synchronized Optional<ConsoleRegistration> load(String deviceId) {
        String id = validId(deviceId);
        String value = preferences.getString(id, null);
        if (value == null) return Optional.empty();
        try {
            JSONObject envelope = new JSONObject(value);
            byte[] iv = decode(envelope.getString("iv"));
            if (iv.length != 12) throw new GeneralSecurityException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, iv));
            cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
            JSONObject data = new JSONObject(new String(cipher.doFinal(decode(envelope.getString("ciphertext"))), StandardCharsets.UTF_8));
            if (data.getInt("version") != 1) throw new GeneralSecurityException();
            return Optional.of(new ConsoleRegistration(data.getBoolean("ps5"), data.getString("name"),
                    decode(data.getString("mac")), decode(data.getString("registrationKey")), decode(data.getString("morning"))));
        } catch (GeneralSecurityException | JSONException | IllegalArgumentException error) {
            throw new IllegalStateException("Kredensial pairing tidak dapat dibaca. Registrasikan ulang konsol.");
        }
    }
    public void delete(String deviceId) {
        if (!preferences.edit().remove(validId(deviceId)).commit()) throw new IllegalStateException("Kredensial gagal dihapus.");
    }
    private SecretKey secretKey() throws GeneralSecurityException {
      synchronized (KEY_LOCK) {
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
            SecretKey existing = (SecretKey) store.getKey(keyAlias, null);
            if (existing != null) return existing;
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).build());
            return generator.generateKey();
        } catch (java.io.IOException error) { throw new GeneralSecurityException(error); }
      }
    }
    private static String validId(String id) { return Device.requireText(id, "ID", 80); }
    private static String encode(byte[] data) { return Base64.encodeToString(data, Base64.NO_WRAP); }
    private static byte[] decode(String data) { return Base64.decode(data, Base64.NO_WRAP); }
}
