package com.deploydulupulangnanti.psxremoteplay;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.deploydulupulangnanti.psxremoteplay.connection.NativeBridge;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Uses only loopback and synthetic credentials; never contacts a real console. */
@RunWith(AndroidJUnit4.class)
public class NativeConnectionSmokeTest {
    @Test public void pinnedProviderLoadsAndRejectsInvalidArguments() {
        assertTrue("Source-built provider must load on the emulator ABI", NativeBridge.available());
        assertEquals("chiaki-ng ca5379f4504af6e63cf5d32b5626de2425b2382e", NativeBridge.libraryVersion());
        assertEquals(0, NativeBridge.register("127.0.0.1", false, new byte[7], 12345678, registrationCallback(new CountDownLatch(1), new AtomicBoolean())));
        assertEquals(0, NativeBridge.register("http://127.0.0.1", false, new byte[8], 12345678, registrationCallback(new CountDownLatch(1), new AtomicBoolean())));
        assertEquals(0, NativeBridge.startSession("127.0.0.1", false, new byte[15], new byte[16], 1280, 720, 60, 10000, false, sessionCallback(new CountDownLatch(1))));
        assertEquals(0, NativeBridge.startSession("127.0.0.1", false, new byte[16], new byte[16], 1280, 720, 25, 10000, false, sessionCallback(new CountDownLatch(1))));
        NativeBridge.stopRegistration(0); NativeBridge.stopRegistration(-1);
        NativeBridge.stopSession(0); NativeBridge.stopSession(-1);
        NativeBridge.setController(-1, 0, 0, 0, 0, 0, 0, 0);
        NativeBridge.setLoginPin(-1, "1234");
    }

    @Test public void loopbackRegistrationFailsBoundedlyAndCanBeStoppedTwice() throws Exception {
        assertTrue(NativeBridge.available());
        CountDownLatch failed = new CountDownLatch(1);
        AtomicBoolean unexpectedSuccess = new AtomicBoolean();
        long handle = NativeBridge.register("127.0.0.1", false, new byte[8], 12345678, registrationCallback(failed, unexpectedSuccess));
        assertTrue("Registration worker should start", handle != 0);
        try {
            assertTrue("Loopback with no console must finish within the protocol timeout", failed.await(12, TimeUnit.SECONDS));
            assertFalse(unexpectedSuccess.get());
        } finally { NativeBridge.stopRegistration(handle); NativeBridge.stopRegistration(handle); }
    }

    @Test public void loopbackSessionReportsQuitAndSurvivesRepeatedInputAndClose() throws Exception {
        assertTrue(NativeBridge.available());
        for (int attempt = 0; attempt < 3; attempt++) {
            CountDownLatch quit = new CountDownLatch(1);
            long handle = NativeBridge.startSession("127.0.0.1", false, new byte[16], new byte[16], 1280, 720, 60, 10000, true, sessionCallback(quit));
            assertTrue("Synthetic loopback session should create a worker", handle != 0);
            try {
                NativeBridge.setController(handle, 0, 0, 0, 0, 0, 0, 0);
                NativeBridge.setController(handle, 0, Float.NaN, Float.POSITIVE_INFINITY, -2, 2, -1, 2);
                NativeBridge.setLoginPin(handle, "1234");
                assertTrue("No console is listening on loopback", quit.await(8, TimeUnit.SECONDS));
            } finally {
                NativeBridge.stopSession(handle); NativeBridge.stopSession(handle);
                NativeBridge.setController(handle, 0, 0, 0, 0, 0, 0, 0);
            }
        }
    }

    private NativeBridge.RegistrationCallback registrationCallback(CountDownLatch completed, AtomicBoolean unexpectedSuccess) {
        return new NativeBridge.RegistrationCallback() {
            @Override public void onSuccess(boolean ps5, String name, byte[] mac, byte[] key, byte[] morning) { unexpectedSuccess.set(true); completed.countDown(); }
            @Override public void onFailure() { completed.countDown(); }
        };
    }
    private NativeBridge.SessionCallback sessionCallback(CountDownLatch quit) {
        return new NativeBridge.SessionCallback() {
            @Override public void onConnected() { }
            @Override public void onLoginPin(boolean incorrect) { }
            @Override public void onQuit(int reason) { quit.countDown(); }
            @Override public boolean onVideo(byte[] annexB) { return false; }
            @Override public void onAudioSettings(int sampleRate, int channels) { }
            @Override public void onAudio(short[] pcm) { }
        };
    }
}
