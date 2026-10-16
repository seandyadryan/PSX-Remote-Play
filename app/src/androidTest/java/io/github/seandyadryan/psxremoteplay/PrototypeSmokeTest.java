package io.github.seandyadryan.psxremoteplay;

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
import io.github.seandyadryan.psxremoteplay.core.Device;
import io.github.seandyadryan.psxremoteplay.core.StreamProfile;
import io.github.seandyadryan.psxremoteplay.data.AppRepository;
import io.github.seandyadryan.psxremoteplay.media.MediaCodecVideoDecoder;
import io.github.seandyadryan.psxremoteplay.media.PcmAudioSink;
import io.github.seandyadryan.psxremoteplay.ui.MainActivity;
import io.github.seandyadryan.psxremoteplay.ui.StreamActivity;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PrototypeSmokeTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    @Test public void deviceAndProfileCrudSurvivesRepositoryRecreation() {
        // Use test APK context so the user's application preferences are never cleared.
        Context context = instrumentation.getContext();
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
        Context context = instrumentation.getContext();
        context.getSharedPreferences("psx_local_v1", Context.MODE_PRIVATE).edit().putString("devices", "invalid-json").commit();
        try { assertTrue(new AppRepository(context).devices().isEmpty()); }
        finally { context.getSharedPreferences("psx_local_v1", Context.MODE_PRIVATE).edit().clear().commit(); }
    }
    @Test public void homeAndDemoLaunchWithTruthfulLabels() {
        Activity home = launch(MainActivity.class);
        try {
            instrumentation.runOnMainSync(() -> assertTrue(contains(home.getWindow().getDecorView(), "Mulai dari demo lokal")));
            Activity demo = launch(StreamActivity.class);
            try {
                instrumentation.runOnMainSync(() -> {
                    assertTrue(contains(demo.getWindow().getDecorView(), "Demo lokal — bukan streaming konsol"));
                    assertTrue(contains(demo.getWindow().getDecorView(), "Frame diterima: 0"));
                });
                // Exercise pause/resume without leaving another activity or process running.
                instrumentation.runOnMainSync(() -> instrumentation.callActivityOnPause(demo));
                instrumentation.runOnMainSync(() -> instrumentation.callActivityOnResume(demo));
                instrumentation.runOnMainSync(() -> assertTrue(contains(demo.getWindow().getDecorView(), "Demo lokal — bukan streaming konsol")));
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
