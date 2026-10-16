package io.github.seandyadryan.psxremoteplay.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import io.github.seandyadryan.psxremoteplay.core.*;
import io.github.seandyadryan.psxremoteplay.data.AppRepository;
import io.github.seandyadryan.psxremoteplay.platform.AndroidController;
import io.github.seandyadryan.psxremoteplay.platform.AppLog;

@SuppressLint("SetTextI18n")
public final class StreamActivity extends Activity implements AndroidController.Listener {
    private SessionController session;
    private AndroidController controller;
    private DemoCanvas canvas;
    private TextView status, input, controllers;
    private StreamProfile profile;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); profile = new AppRepository(this).selectedProfile();
        LinearLayout frame = Ui.column(this); frame.setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this); frame.addView(scroll);
        LinearLayout page = Ui.column(this); int pad = Ui.dp(this, 20); page.setPadding(pad, pad, pad, pad); scroll.addView(page);
        Ui.text(this, page, "SESSION LAB", 12, Ui.ACCENT, true);
        Ui.text(this, page, "Demo lokal", 28, Ui.TEXT, true);
        status = Ui.text(this, page, "Menyiapkan demo", 14, Ui.ACCENT, false);
        canvas = new DemoCanvas(this); page.addView(canvas, new LinearLayout.LayoutParams(-1, Ui.dp(this, 220)));
        Ui.text(this, page, profile.name + " · " + profile.summary(), 13, Ui.MUTED, false);
        Ui.text(this, page, "Frame diterima: 0 · Audio: tidak diputar\nLatensi jaringan: tidak diukur\nParameter video/audio tersimpan, belum diterapkan pada stream.", 13, Ui.MUTED, false);
        LinearLayout panel = Ui.card(this, page);
        Ui.text(this, panel, "CONTROLLER INPUT", 11, Ui.ACCENT, true);
        controllers = Ui.text(this, panel, "", 14, Ui.TEXT, true);
        input = Ui.text(this, panel, "Belum ada input", 13, Ui.MUTED, false);
        Ui.text(this, panel, "Stick kiri menggerakkan titik demo. Tombol hanya ditampilkan di sini; tidak dikirim ke konsol.", 13, Ui.MUTED, false);
        Ui.button(this, page, "Akhiri demo", true, view -> finish());
        setContentView(frame); Ui.insets(frame);
        session = new SessionController(new DemoTransport(), (value, message) -> {
            status.setText(message); AppLog.event("session_" + value.name());
            canvas.running(value == SessionController.State.DEMO_ACTIVE);
        });
    }
    @Override public void onResume() {
        super.onResume(); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        session.start(new Device("demo", "Demo lokal", "localhost"), profile);
        controller = new AndroidController(this, profile.deadZone, this);
    }
    @Override public void onPause() {
        if (controller != null) { controller.close(); controller = null; }
        session.close(); canvas.running(false);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onPause();
    }
    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus); if (!focus && controller != null) controller.reset();
    }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return controller != null && controller.motion(event) || super.dispatchGenericMotionEvent(event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        return controller != null && controller.key(event) || super.dispatchKeyEvent(event);
    }
    @Override public void onInput(ControllerInput.Snapshot value) {
        session.sendInput(value); canvas.input(value);
        input.setText(String.format(Locale.ROOT, "L %+.2f / %+.2f    R %+.2f / %+.2f\nLT %.2f    RT %.2f    Buttons 0x%04X",
                value.leftX, value.leftY, value.rightX, value.rightY, value.leftTrigger, value.rightTrigger, value.buttons));
    }
    @Override public void onDevicesChanged(int count) { controllers.setText(count + " controller terdeteksi"); }
}
