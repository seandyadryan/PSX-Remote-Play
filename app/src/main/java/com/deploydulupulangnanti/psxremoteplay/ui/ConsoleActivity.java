package com.deploydulupulangnanti.psxremoteplay.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.deploydulupulangnanti.psxremoteplay.connection.NativeBridge;
import com.deploydulupulangnanti.psxremoteplay.core.ConsoleRegistration;
import com.deploydulupulangnanti.psxremoteplay.core.ConsoleWakeup;
import com.deploydulupulangnanti.psxremoteplay.core.Device;
import com.deploydulupulangnanti.psxremoteplay.data.AppRepository;
import com.deploydulupulangnanti.psxremoteplay.data.RegistrationStore;

/** One console's registration, wake-up and stream actions. No secrets in intents or views. */
@SuppressLint("SetTextI18n")
public final class ConsoleActivity extends Activity {
    public static final String DEVICE_ID = "device_id";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ConsoleWakeup wakeup = new ConsoleWakeup();
    private Device device;
    private RegistrationStore store;
    private TextView status;
    private volatile int generation;
    private volatile boolean foreground;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = new RegistrationStore(this);
        String id = getIntent().getStringExtra(DEVICE_ID);
        for (Device item : new AppRepository(this).devices()) if (item.id.equals(id)) device = item;
        if (device == null) finish();
    }
    @Override public void onResume() { super.onResume(); foreground = true; if (device != null) render(); }
    private void render() {
        LinearLayout frame = Ui.column(this); frame.setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this); frame.addView(scroll);
        LinearLayout page = Ui.column(this); int pad = Ui.dp(this, 24); page.setPadding(pad,pad,pad,pad); scroll.addView(page);
        Ui.text(this, page, "CONSOLE CONNECTION", 12, Ui.ACCENT, true);
        Ui.text(this, page, device.name, 28, Ui.TEXT, true);
        Ui.text(this, page, device.host, 14, Ui.MUTED, false);
        boolean paired = store.has(device.id);
        status = Ui.text(this, page, paired ? "Pairing credentials are saved on this device." : "Register your console before wake-up or streaming.", 15, Ui.TEXT, false);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        Ui.text(this, page, "Use the same local network and enable Remote Play on your console. For wake-up, enable Stay Connected to the Internet and turning on the console from the network in Rest Mode.", 14, Ui.MUTED, false);
        Ui.button(this, page, paired ? "Register console again" : "Register console", !paired, view ->
                startActivity(new Intent(this, RegistrationActivity.class).putExtra(DEVICE_ID, device.id)));
        Ui.button(this, page, "Wake up console", false, view -> sendWakeup()).setEnabled(paired);
        Ui.button(this, page, "Start console stream", true, view ->
                startActivity(new Intent(this, RemoteSessionActivity.class).putExtra(DEVICE_ID, device.id)))
                .setEnabled(paired && NativeBridge.available());
        if (!NativeBridge.available()) Ui.text(this, page, "The connection engine is unavailable. Install a complete arm64-v8a or x86_64 build.", 14, Ui.MUTED, false);
        if (paired) Ui.button(this, page, "Delete local pairing", false, view -> new AlertDialog.Builder(this)
                .setMessage("Delete local credentials? Console data will remain. You will need to register again to connect.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) -> {
                    int attempt = ++generation;
                    worker.execute(() -> {
                        String error = null;
                        try { store.delete(device.id); } catch (IllegalStateException problem) { error = problem.getMessage(); }
                        final String message = error;
                        main.post(() -> { if (foreground && generation == attempt) { render(); if (message != null) status.setText(message); } });
                    });
                }).show());
        Ui.text(this, page, "Connection engine: Chiaki 1.6.5 · AVC/H.264 video and Opus audio. Firmware and network compatibility must be tested with your console.", 13, Ui.MUTED, false);
        Ui.button(this, page, "Back", false, view -> finish());
        setContentView(frame); Ui.insets(frame);
    }
    private void sendWakeup() {
        final int attempt = ++generation;
        wakeup.close(); status.setText("Preparing wake-up…");
        worker.execute(() -> {
            try {
                ConsoleRegistration registration = store.load(device.id).orElseThrow(() -> new IllegalStateException("Register your console first."));
                main.post(() -> {
                  if (!foreground || generation != attempt) return;
                  try { wakeup.send(device.host, registration, new ConsoleWakeup.Listener() {
                    @Override public void onSent() { show("Wake-up packets sent. Search again to check the console status."); }
                    @Override public void onFailure(String message) { show(message); }
                    private void show(String message) { main.post(() -> { if (foreground && generation == attempt) status.setText(message); }); }
                  }); } catch (IllegalArgumentException invalid) {
                      status.setText("Wake-up credentials are invalid. Register your console again.");
                  }
                });
            } catch (IllegalStateException | IllegalArgumentException problem) {
                main.post(() -> { if (foreground && generation == attempt) status.setText("Wake-up credentials are unavailable or invalid. Register your console again."); });
            }
        });
    }
    @Override public void onPause() { foreground = false; generation++; wakeup.close(); super.onPause(); }
    @Override public void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
}
