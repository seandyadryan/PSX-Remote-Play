package com.deploydulupulangnanti.psxremoteplay;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.deploydulupulangnanti.psxremoteplay.core.ConsoleRegistration;
import com.deploydulupulangnanti.psxremoteplay.core.Device;
import com.deploydulupulangnanti.psxremoteplay.data.AppRepository;
import com.deploydulupulangnanti.psxremoteplay.data.RegistrationStore;
import com.deploydulupulangnanti.psxremoteplay.ui.ConsoleActivity;
import com.deploydulupulangnanti.psxremoteplay.ui.RegistrationActivity;
import com.deploydulupulangnanti.psxremoteplay.ui.RemoteSessionActivity;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Creates and removes only a unique loopback fixture on this task's test emulator. */
@RunWith(AndroidJUnit4.class)
public class ConnectionUiTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    @Test public void consoleActionsAndRegistrationRejectInvalidInputWithoutLosingPairing() throws Exception {
        String id = fixture();
        Activity console = null, registration = null;
        try {
            console = launch(ConsoleActivity.class,id);
            final Activity actions = console;
            instrumentation.runOnMainSync(() -> {
                assertNotNull(text(actions.getWindow().getDecorView(),"Register console again"));
                assertTrue(text(actions.getWindow().getDecorView(),"Wake up console").isEnabled());
                assertTrue(text(actions.getWindow().getDecorView(),"Start console stream").isEnabled());
            });
            registration = launch(RegistrationActivity.class,id);
            final Activity form = registration;
            instrumentation.runOnMainSync(() -> {
                ((EditText) described(form.getWindow().getDecorView(),"PSN account ID")).setText("invalid");
                ((EditText) described(form.getWindow().getDecorView(),"8-digit console PIN")).setText("123");
                text(form.getWindow().getDecorView(),"Start registration").performClick();
                assertNotNull(text(form.getWindow().getDecorView(),"Check your PSN account ID"));
            });
            assertTrue(new RegistrationStore(context).load(id).isPresent());
            // All text in this screenshot is a synthetic loopback fixture, never user data.
            try (android.os.ParcelFileDescriptor.AutoCloseInputStream capture = new android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.getUiAutomation().executeShellCommand("screencap -p /data/local/tmp/psx-registration-form.png"))) {
                byte[] buffer = new byte[1024]; while (capture.read(buffer) != -1) { /* Wait for screencap to finish. */ }
            }
        } finally { finish(registration); finish(console); clear(id); }
    }
    @Test public void loopbackSessionScreenPausesAndResumesWithoutClaimingVideo() throws Exception {
        String id = fixture(); Activity session = null;
        try {
            session = launch(RemoteSessionActivity.class,id); final Activity screen = session;
            instrumentation.runOnMainSync(() -> assertNotNull(text(screen.getWindow().getDecorView(),"Video received: 0 units")));
            instrumentation.runOnMainSync(() -> instrumentation.callActivityOnPause(screen));
            instrumentation.runOnMainSync(() -> instrumentation.callActivityOnResume(screen));
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                assertNotNull(text(screen.getWindow().getDecorView(),"Video received: 0 units"));
                assertNotNull(text(screen.getWindow().getDecorView(),"Disconnect"));
            });
        } finally { finish(session); clear(id); }
    }
    private String fixture() {
        String id = "ui-" + UUID.randomUUID();
        new AppRepository(context).saveDevice(new Device(id,"Konsol uji loopback","127.0.0.1"));
        byte[] key = new byte[16]; byte[] ascii = "1a2b3c4d".getBytes(StandardCharsets.US_ASCII); System.arraycopy(ascii,0,key,0,ascii.length);
        new RegistrationStore(context).save(id,new ConsoleRegistration(false,"Fixture",new byte[6],key,new byte[16]));
        return id;
    }
    private void clear(String id) { new RegistrationStore(context).delete(id); new AppRepository(context).deleteDevice(id); }
    private Activity launch(Class<? extends Activity> type,String id) {
        Activity result = instrumentation.startActivitySync(new Intent(context,type).putExtra(ConsoleActivity.DEVICE_ID,id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync(); return result;
    }
    private void finish(Activity activity) { if (activity != null) { instrumentation.runOnMainSync(activity::finish); instrumentation.waitForIdleSync(); } }
    private TextView text(View root,String match) {
        if (root instanceof TextView && ((TextView)root).getText().toString().contains(match)) return (TextView)root;
        if (root instanceof ViewGroup) for (int i=0;i<((ViewGroup)root).getChildCount();i++) { TextView found=text(((ViewGroup)root).getChildAt(i),match); if (found!=null) return found; }
        return null;
    }
    private View described(View root,String match) {
        if (match.contentEquals(root.getContentDescription()==null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup) for (int i=0;i<((ViewGroup)root).getChildCount();i++) { View found=described(((ViewGroup)root).getChildAt(i),match); if (found!=null) return found; }
        return null;
    }
}
