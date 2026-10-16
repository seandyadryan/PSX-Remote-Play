package com.deploydulupulangnanti.psxremoteplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.deploydulupulangnanti.psxremoteplay.core.ConsoleRegistration;
import com.deploydulupulangnanti.psxremoteplay.data.RegistrationStore;
import java.security.KeyStore;
import java.util.Arrays;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RegistrationStoreTest {
    private Context context;
    private SharedPreferences preferences;
    @Before public void setUp() {
        // Instrumentation runs as the target UID; use an isolated namespace in writable storage.
        context = new TestStorageContext(InstrumentationRegistry.getInstrumentation().getTargetContext());
        preferences = context.getSharedPreferences("psx_registrations_v1", Context.MODE_PRIVATE);
        assertTrue(preferences.edit().clear().commit());
    }
    @After public void cleanUp() throws Exception {
        assertTrue(preferences.edit().clear().commit());
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore"); keys.load(null);
        keys.deleteEntry(context.getPackageName() + ".console-registration.v1");
        context.deleteSharedPreferences("psx_registrations_v1");
    }
    @Test public void encryptedResultRoundTripsAndDeletionSurvivesNewStore() {
        RegistrationStore store = new RegistrationStore(context);
        ConsoleRegistration expected = fixture();
        store.save("console-a", expected);
        String raw = preferences.getString("console-a", "");
        assertFalse(raw.contains(expected.name));
        assertFalse(raw.contains("registrationKey"));
        assertFalse(raw.contains(Base64.encodeToString(expected.registrationKey(), Base64.NO_WRAP)));
        ConsoleRegistration actual = new RegistrationStore(context).load("console-a").get();
        assertEquals(expected.ps5, actual.ps5); assertEquals(expected.name, actual.name);
        assertArrayEquals(expected.serverMac(), actual.serverMac());
        assertArrayEquals(expected.registrationKey(), actual.registrationKey());
        assertArrayEquals(expected.morning(), actual.morning());
        store.delete("console-a");
        assertFalse(new RegistrationStore(context).has("console-a"));
        assertFalse(new RegistrationStore(context).load("console-a").isPresent());
    }
    @Test public void ciphertextCannotBeCopiedToAnotherDeviceId() {
        RegistrationStore store = new RegistrationStore(context);
        store.save("console-a", fixture());
        assertTrue(preferences.edit().putString("console-b", preferences.getString("console-a", "")).commit());
        expectUnreadable(store, "console-b");
        assertTrue(store.load("console-a").isPresent());
    }
    @Test public void changedCiphertextIsRejected() throws Exception {
        RegistrationStore store = new RegistrationStore(context);
        store.save("console-a", fixture());
        JSONObject envelope = new JSONObject(preferences.getString("console-a", ""));
        byte[] ciphertext = Base64.decode(envelope.getString("ciphertext"), Base64.NO_WRAP);
        ciphertext[ciphertext.length / 2] ^= 1;
        envelope.put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP));
        assertTrue(preferences.edit().putString("console-a", envelope.toString()).commit());
        expectUnreadable(store, "console-a");
        store.delete("console-a"); assertFalse(store.has("console-a"));
    }
    private void expectUnreadable(RegistrationStore store, String id) {
        try { store.load(id); fail("Modified authenticated credentials must be rejected"); }
        catch (IllegalStateException expected) { assertFalse(expected.getMessage().contains("Keystore-only fixture")); }
    }
    private ConsoleRegistration fixture() {
        byte[] key = new byte[16], morning = new byte[16];
        Arrays.fill(key, (byte) 0x4b); Arrays.fill(morning, (byte) 0x39);
        return new ConsoleRegistration(true, "Keystore-only fixture", new byte[]{1, 2, 3, 4, 5, 6}, key, morning);
    }
}
