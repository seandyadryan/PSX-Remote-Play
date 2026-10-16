package com.deploydulupulangnanti.psxremoteplay.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import com.deploydulupulangnanti.psxremoteplay.connection.NativeStreamConnection;
import com.deploydulupulangnanti.psxremoteplay.core.ControllerInput;
import com.deploydulupulangnanti.psxremoteplay.core.Device;
import com.deploydulupulangnanti.psxremoteplay.core.StreamProfile;
import com.deploydulupulangnanti.psxremoteplay.data.AppRepository;
import com.deploydulupulangnanti.psxremoteplay.data.RegistrationStore;
import com.deploydulupulangnanti.psxremoteplay.platform.AndroidController;

@SuppressLint("SetTextI18n")
public final class RemoteSessionActivity extends Activity implements SurfaceHolder.Callback, AndroidController.Listener {
    private Device device;
    private StreamProfile profile;
    private SurfaceView video;
    private TextView status, stats;
    private NativeStreamConnection connection;
    private AndroidController controller;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean resumed, surfaceReady;
    private boolean audioUnavailable;
    private AlertDialog pinDialog;
    private final Handler main = new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        AppRepository repository = new AppRepository(this);
        String id = getIntent().getStringExtra(ConsoleActivity.DEVICE_ID);
        for (Device item : repository.devices()) if (item.id.equals(id)) device = item;
        if (device == null) { finish(); return; }
        profile = repository.selectedProfile(); audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        LinearLayout frame = Ui.column(this); frame.setBackgroundColor(Ui.BG);
        LinearLayout page = Ui.column(this); int pad = Ui.dp(this,12); page.setPadding(pad,pad,pad,pad); frame.addView(page, new LinearLayout.LayoutParams(-1,-1));
        status = Ui.text(this, page, "Preparing console connection…", 16, Ui.ACCENT, true);
        FrameLayout viewport = new FrameLayout(this); page.addView(viewport, new LinearLayout.LayoutParams(-1,0,1));
        video = new SurfaceView(this); viewport.addView(video, new FrameLayout.LayoutParams(-1,-1)); video.getHolder().addCallback(this);
        viewport.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom) -> {
            int width = Math.min(right - left, Math.max(1, (bottom - top) * 16 / 9));
            FrameLayout.LayoutParams size = new FrameLayout.LayoutParams(width, width * 9 / 16, android.view.Gravity.CENTER);
            video.setLayoutParams(size);
        });
        stats = Ui.text(this, page, "Video received: 0 units · " + profile.summary(), 12, Ui.MUTED, false);
        Ui.text(this, page, "Use an Android gamepad. Input is sent to your registered console.", 12, Ui.MUTED, false);
        LinearLayout actions = Ui.column(this); actions.setOrientation(LinearLayout.HORIZONTAL); page.addView(actions);
        systemButton(actions, "PS", 1 << 15); systemButton(actions, "Options", 1 << 12); systemButton(actions, "Touchpad click", 1 << 14);
        Ui.button(this, page, "Disconnect", true, view -> finish());
        setContentView(frame); Ui.insets(frame);
    }
    private void systemButton(LinearLayout row, String label, int mask) {
        Button button = Ui.button(this, row, label, false, view -> {
            NativeStreamConnection current = connection;
            if (current == null) return;
            current.systemButtons(mask);
            main.postDelayed(() -> { if (connection == current) current.systemButtons(0); }, 120);
        });
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0,-2,1); size.setMargins(Ui.dp(this,3),0,Ui.dp(this,3),0);
        button.setLayoutParams(size);
    }
    @Override public void onResume() { super.onResume(); resumed = true; if (device != null) startIfReady(); }
    private void startIfReady() {
        if (!resumed || !surfaceReady || connection != null) return;
        if (profile.audioEnabled) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                    .setOnAudioFocusChangeListener(change -> {
                        if (change != AudioManager.AUDIOFOCUS_GAIN) { stop(); status.setText("Session stopped after losing audio focus. Open the session again to continue."); }
                    }).build();
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                focusRequest = null; status.setText("Audio focus is unavailable. Disable profile audio or try again."); return;
            }
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        connection = new NativeStreamConnection(new RegistrationStore(this), new NativeStreamConnection.Listener() {
            @Override public void onConnected() { status.setText("Console connected · waiting for video"); }
            @Override public void onLoginPin(boolean incorrect) { requestLoginPin(incorrect); }
            @Override public void onVideo(long units) { status.setText("Console stream"); stats.setText("Video received: " + units + " units · " + profile.summary() + (audioUnavailable ? " · Audio unavailable" : "")); }
            @Override public void onAudioUnavailable() { audioUnavailable = true; stats.setText("Audio unavailable · video continues"); }
            @Override public void onFailure(String message) { stop(); status.setText(message); }
        });
        connection.open(device, profile, video.getHolder().getSurface());
        controller = new AndroidController(this, profile.deadZone, this);
    }
    private void requestLoginPin(boolean incorrect) {
        if (pinDialog != null) pinDialog.dismiss();
        EditText pin = new EditText(this); pin.setSingleLine(); pin.setSaveEnabled(false);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)}); pin.setContentDescription("4-digit console login PIN");
        pinDialog = new AlertDialog.Builder(this).setTitle(incorrect ? "Incorrect login PIN" : "Console user login PIN")
                .setMessage("Enter the 4-digit user login PIN configured on your console, not the 8-digit registration PIN.")
                .setView(pin).setNegativeButton("Disconnect", (dialog, which) -> finish()).setPositiveButton("Send", null).create();
        pinDialog.setOnShowListener(dialog -> pinDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = pin.getText().toString();
            if (!value.matches("[0-9]{4}")) { pin.setError("Enter 4 digits."); return; }
            if (connection != null) connection.loginPin(value); pin.setText(""); pinDialog.dismiss();
        }));
        pinDialog.show();
    }
    private void stop() {
        if (controller != null) { controller.close(); controller = null; }
        if (connection != null) { connection.close(); connection = null; }
        if (pinDialog != null) { pinDialog.dismiss(); pinDialog = null; }
        if (focusRequest != null) { audioManager.abandonAudioFocusRequest(focusRequest); focusRequest = null; }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override public void onPause() { resumed = false; stop(); super.onPause(); }
    @Override public void surfaceCreated(SurfaceHolder holder) { surfaceReady = true; startIfReady(); }
    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { surfaceReady = false; stop(); }
    @Override public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused); if (!focused && controller != null) controller.reset(); }
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) { return controller != null && controller.motion(event) || super.dispatchGenericMotionEvent(event); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) { return controller != null && controller.key(event) || super.dispatchKeyEvent(event); }
    @Override public void onInput(ControllerInput.Snapshot input) { if (connection != null) connection.input(input); }
    @Override public void onDevicesChanged(int count) { }
}
