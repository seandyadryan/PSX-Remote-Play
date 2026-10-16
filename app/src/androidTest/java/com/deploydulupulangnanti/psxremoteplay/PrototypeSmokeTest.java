package com.deploydulupulangnanti.psxremoteplay;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.MediaFormat;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.deploydulupulangnanti.psxremoteplay.core.Device;
import com.deploydulupulangnanti.psxremoteplay.core.DiscoveryProtocol;
import com.deploydulupulangnanti.psxremoteplay.core.DiscoveredConsole;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import com.deploydulupulangnanti.psxremoteplay.core.StreamProfile;
import com.deploydulupulangnanti.psxremoteplay.data.AppRepository;
import com.deploydulupulangnanti.psxremoteplay.media.MediaCodecVideoDecoder;
import com.deploydulupulangnanti.psxremoteplay.media.PcmAudioSink;
import com.deploydulupulangnanti.psxremoteplay.ui.MainActivity;
import com.deploydulupulangnanti.psxremoteplay.ui.StreamActivity;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PrototypeSmokeTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    @Test public void deviceAndProfileCrudSurvivesRepositoryRecreation() {
        // Isolate preference names while keeping the target process's writable UID/context.
        Context context = new TestStorageContext(instrumentation.getTargetContext());
        context.getSharedPreferences("psx_local_v1", Context.MODE_PRIVATE).edit().clear().commit();
        try {
            AppRepository repository = new AppRepository(context);
            repository.saveDevice(new Device("test-device", "Lab", "192.168.1.10"));
            repository.saveDevice(new Device("test-device", "Updated lab", "console.local"));
            repository.saveProfile(new StreamProfile("test-profile", "Custom", 1080, 30, 20, false, 0.2f));
            AppRepository reloaded = new AppRepository(context);
            assertEquals(1, reloaded.devices().size());
            assertEquals("Updated lab", reloaded.devices().get(0).name);
            assertEquals(1080, reloaded.selectedProfile().height);
            assertFalse(reloaded.selectedProfile().audioEnabled);
            reloaded.deleteDevice("test-device"); reloaded.deleteProfile("test-profile");
            assertTrue(new AppRepository(context).devices().isEmpty());
            assertEquals("balanced", new AppRepository(context).selectedProfile().id);
        } finally { context.getSharedPreferences("psx_local_v1", Context.MODE_PRIVATE).edit().clear().commit(); }
    }
    @Test public void malformedStorageDoesNotCrashOrCreateDevices() {
        Context context = new TestStorageContext(instrumentation.getTargetContext());
        context.getSharedPreferences("psx_local_v1", Context.MODE_PRIVATE).edit().putString("devices", "invalid-json").commit();
        try { assertTrue(new AppRepository(context).devices().isEmpty()); }
        finally { context.getSharedPreferences("psx_local_v1", Context.MODE_PRIVATE).edit().clear().commit(); }
    }
    @Test public void discoverySaveMergesManualAddressAndTracksIpChanges() throws Exception {
        Context context = new TestStorageContext(instrumentation.getTargetContext());
        context.getSharedPreferences("psx_local_v1", Context.MODE_PRIVATE).edit().clear().commit();
        try {
            AppRepository repository = new AppRepository(context);
            repository.saveDevice(new Device("manual", "My custom console", "192.168.1.10"));
            DiscoveredConsole first = advertisedConsole("192.168.1.10");
            repository.saveDiscoveredConsole(first);
            repository.saveDiscoveredConsole(first);
            AppRepository reloaded = new AppRepository(context);
            assertEquals(1, reloaded.devices().size());
            assertEquals("My custom console", reloaded.devices().get(0).name);
            assertEquals("manual", reloaded.devices().get(0).id);
            assertEquals("manual", reloaded.deviceIdFor(first));
            reloaded.saveDevice(new Device("second-manual-address", "Duplicate address", "192.168.1.20"));
            reloaded.saveDiscoveredConsole(advertisedConsole("192.168.1.20"));
            assertEquals(1, new AppRepository(context).devices().size());
            assertEquals("192.168.1.20", new AppRepository(context).devices().get(0).host);
            assertEquals("manual", new AppRepository(context).devices().get(0).id);
            assertEquals("My custom console", new AppRepository(context).devices().get(0).name);
        } finally { context.getSharedPreferences("psx_local_v1", Context.MODE_PRIVATE).edit().clear().commit(); }
    }
    private DiscoveredConsole advertisedConsole(String host) throws Exception {
        byte[] bytes = ("HTTP/1.1 200 Ok\r\nhost-id:001122AABBCC\r\nhost-type:PS5\r\n"
                + "host-name:Fixture console\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        return DiscoveryProtocol.parse(new DatagramPacket(bytes, bytes.length, InetAddress.getByName(host), 9302)).get();
    }
    @Test public void homeAndDemoLaunchWithTruthfulLabels() {
        Activity home = launch(MainActivity.class);
        try {
            instrumentation.runOnMainSync(() -> {
                assertTrue(contains(home.getWindow().getDecorView(), "Explore the local demo"));
                assertTrue(contains(home.getWindow().getDecorView(), "Find your console"));
                assertTrue(contains(home.getWindow().getDecorView(), "Search local network"));
                assertTrue(contains(home.getWindow().getDecorView(), "Search by IP address"));
            });
            Activity demo = launch(StreamActivity.class);
            try {
                instrumentation.runOnMainSync(() -> {
                    assertTrue(contains(demo.getWindow().getDecorView(), "Local demo — no console stream"));
                    assertTrue(contains(demo.getWindow().getDecorView(), "Frames received: 0"));
                });
                // Exercise pause/resume without leaving another activity or process running.
                instrumentation.runOnMainSync(() -> instrumentation.callActivityOnPause(demo));
                instrumentation.runOnMainSync(() -> instrumentation.callActivityOnResume(demo));
                instrumentation.runOnMainSync(() -> assertTrue(contains(demo.getWindow().getDecorView(), "Local demo — no console stream")));
            } finally { instrumentation.runOnMainSync(demo::finish); instrumentation.waitForIdleSync(); }
        } finally { instrumentation.runOnMainSync(home::finish); instrumentation.waitForIdleSync(); }
    }
    @Test public void pcmSinkAcceptsGeneratedSilenceAndClosesTwice() {
        PcmAudioSink sink = new PcmAudioSink();
        try {
            sink.configure(48000, 2);
            int consumed = sink.write(new short[960], 0, 960);
            assertTrue(consumed >= 0 && consumed <= 960);
        } finally { sink.close(); sink.close(); }
    }
    @Test public void avcDecoderConfiguresSurfaceAndReleasesWithoutClaimingFrames() throws Exception {
        SurfaceTexture texture = new SurfaceTexture(false);
        Surface surface = new Surface(texture);
        MediaCodecVideoDecoder decoder = new MediaCodecVideoDecoder();
        try {
            decoder.configure(MediaFormat.createVideoFormat("video/avc", 128, 128), surface);
            assertNotEquals("none", decoder.codecName());
            decoder.drain();
        } finally { decoder.close(); decoder.close(); surface.release(); texture.release(); }
        // No compressed bitstream was fed, so this test establishes setup/cleanup only.
    }
    private Activity launch(Class<? extends Activity> type) {
        Intent intent = new Intent(instrumentation.getTargetContext(), type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = instrumentation.startActivitySync(intent); instrumentation.waitForIdleSync(); return activity;
    }
    private boolean contains(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (contains(group.getChildAt(i), text)) return true;
        }
        return false;
    }
}
