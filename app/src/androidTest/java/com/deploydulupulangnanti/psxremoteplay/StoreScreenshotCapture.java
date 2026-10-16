package com.deploydulupulangnanti.psxremoteplay;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.deploydulupulangnanti.psxremoteplay.core.Device;
import com.deploydulupulangnanti.psxremoteplay.data.AppRepository;
import com.deploydulupulangnanti.psxremoteplay.ui.ConsoleActivity;
import com.deploydulupulangnanti.psxremoteplay.ui.MainActivity;
import com.deploydulupulangnanti.psxremoteplay.ui.RegistrationActivity;
import java.util.UUID;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Opt-in capture utility for a disposable emulator; no fabricated discovery/pairing/session success. */
@RunWith(AndroidJUnit4.class)
public final class StoreScreenshotCapture {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    @Test public void captureActualScreens() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        Assume.assumeTrue("true".equals(arguments.getString("storeScreenshots")));
        String prefix = arguments.getString("capturePrefix", "phone");
        if (!prefix.matches("phone|desktop")) throw new IllegalArgumentException("Invalid capture prefix");
        Activity home = null, form = null;
        String id = "store-preview-" + UUID.randomUUID();
        try {
            home = launch(MainActivity.class, null);
            capture(prefix, "01-home");
            final Activity screen = home;
            instrumentation.runOnMainSync(() -> {
                View heading = text(screen.getWindow().getDecorView(), "Find your console");
                if (heading == null) throw new AssertionError("Discovery card missing");
                View card = (View) heading.getParent();
                card.requestRectangleOnScreen(new Rect(0, 0, card.getWidth(), card.getHeight()), true);
            });
            instrumentation.waitForIdleSync(); capture(prefix, "02-discovery");
            instrumentation.runOnMainSync(() -> {
                TextView settings = text(screen.getWindow().getDecorView(), "Edit settings");
                if (settings == null) throw new AssertionError("Profile settings missing");
                settings.performClick();
            });
            instrumentation.waitForIdleSync(); capture(prefix, "03-profiles");
            instrumentation.runOnMainSync(home::finish); home = null; instrumentation.waitForIdleSync();
            // RFC 5737 example address, saved only for this screenshot fixture. No network call is made.
            new AppRepository(context).saveDevice(new Device(id, "Setup example · no console connected", "192.0.2.10"));
            form = launch(RegistrationActivity.class, id);
            capture(prefix, "04-registration");
        } finally {
            if (form != null) { Activity item = form; instrumentation.runOnMainSync(item::finish); }
            if (home != null) { Activity item = home; instrumentation.runOnMainSync(item::finish); }
            new AppRepository(context).deleteDevice(id);
            instrumentation.waitForIdleSync();
        }
    }
    private Activity launch(Class<? extends Activity> type, String id) {
        Intent intent = new Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (id != null) intent.putExtra(ConsoleActivity.DEVICE_ID, id);
        Activity activity = instrumentation.startActivitySync(intent);
        instrumentation.waitForIdleSync(); return activity;
    }
    private void capture(String prefix, String name) throws Exception {
        instrumentation.waitForIdleSync();
        // Wait for a rendered frame via Choreographer, rather than timing a guessed animation delay.
        java.util.concurrent.CountDownLatch frame = new java.util.concurrent.CountDownLatch(1);
        instrumentation.runOnMainSync(() -> android.view.Choreographer.getInstance().postFrameCallback(time -> frame.countDown()));
        if (!frame.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("No UI frame");
        String path = "/data/local/tmp/psx-store-" + prefix + "-" + name + ".png";
        try (ParcelFileDescriptor.AutoCloseInputStream command = new ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand("screencap -p " + path))) {
            byte[] buffer = new byte[1024]; while (command.read(buffer) != -1) { }
        }
    }
    private TextView text(View root, String value) {
        if (root instanceof TextView && value.contentEquals(((TextView) root).getText())) return (TextView) root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            TextView found = text(((ViewGroup) root).getChildAt(i), value); if (found != null) return found;
        }
        return null;
    }
}
