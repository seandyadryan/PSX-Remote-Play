package com.deploydulupulangnanti.psxremoteplay.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.Arrays;
import com.deploydulupulangnanti.psxremoteplay.connection.NativeBridge;
import com.deploydulupulangnanti.psxremoteplay.connection.RegistrationClient;
import com.deploydulupulangnanti.psxremoteplay.core.ConsoleRegistration;
import com.deploydulupulangnanti.psxremoteplay.core.Device;
import com.deploydulupulangnanti.psxremoteplay.core.RegistrationInput;
import com.deploydulupulangnanti.psxremoteplay.data.AppRepository;
import com.deploydulupulangnanti.psxremoteplay.data.RegistrationStore;

@SuppressLint("SetTextI18n")
public final class RegistrationActivity extends Activity {
    private Device device;
    private RegistrationClient client;
    private Spinner type;
    private EditText account, pin;
    private TextView status;
    private Button register;
    private boolean busy;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra(ConsoleActivity.DEVICE_ID);
        for (Device item : new AppRepository(this).devices()) if (item.id.equals(id)) device = item;
        if (device == null) { finish(); return; }
        LinearLayout frame = Ui.column(this); frame.setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this); frame.addView(scroll);
        LinearLayout page = Ui.column(this); int pad = Ui.dp(this,24); page.setPadding(pad,pad,pad,pad); scroll.addView(page);
        Ui.text(this, page, "Console registration", 28, Ui.TEXT, true);
        Ui.text(this, page, device.name + " · " + device.host, 15, Ui.MUTED, false);
        Ui.text(this, page, "PS5: Settings → System → Remote Play → Link Device.\nPS4: Settings → Remote Play Connection Settings → Add Device.\nEnter the 8-digit PIN before it expires.", 15, Ui.TEXT, false);
        Ui.text(this, page, "Console type", 14, Ui.MUTED, false);
        type = new Spinner(this); type.setContentDescription("Console type");
        type.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"PS5", "PS4 (firmware 8 or later)"}));
        page.addView(type);
        Ui.text(this, page, "PSN account ID (decimal or Base64)", 14, Ui.MUTED, false);
        account = new EditText(this); account.setSingleLine(); account.setSaveEnabled(false);
        account.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        account.setContentDescription("PSN account ID"); page.addView(account);
        Ui.text(this, page, "Use a numeric account ID or 12-character Base64 value, not a username or email. It must belong to the account on your console. Your PSN password is never requested. Automatic PSN sign-in is not available.", 13, Ui.MUTED, false);
        Ui.text(this, page, "Link-device PIN (8 digits)", 14, Ui.MUTED, false);
        pin = new EditText(this); pin.setSingleLine(); pin.setSaveEnabled(false);
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)}); pin.setContentDescription("8-digit console PIN"); page.addView(pin);
        status = Ui.text(this, page, "Your console and Android device must be on the same local network.", 14, Ui.TEXT, false);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        register = Ui.button(this, page, "Start registration", true, view -> start());
        Ui.button(this, page, "Cancel registration", false, view -> cancel());
        Ui.button(this, page, "Back", false, view -> finish());
        setContentView(frame); Ui.insets(frame);
    }
    private void start() {
        if (busy) return;
        if (!NativeBridge.available()) { status.setText("The connection engine is unavailable. Install a complete build for this device ABI."); return; }
        byte[] id;
        int number;
        try { id = RegistrationInput.accountId(account.getText().toString().trim()); number = RegistrationInput.pin(pin.getText().toString()); }
        catch (IllegalArgumentException problem) { status.setText("Check your PSN account ID (number or Base64) and 8-digit PIN."); return; }
        client = new RegistrationClient(new RegistrationStore(this), device.id);
        busy = true; controls(false); status.setText("Registering… Waiting for your console to respond.");
        client.start(device.host, type.getSelectedItemPosition() == 0, id, number, new RegistrationClient.Listener() {
            @Override public void onSuccess(ConsoleRegistration result) {
                busy = false; pin.setText(""); status.setText("Registration complete. Credentials are saved encrypted."); finish();
            }
            @Override public void onFailure(String message) {
                busy = false; controls(true); status.setText(message); pin.setText("");
                if (client != null) { client.close(); client = null; }
            }
        });
        Arrays.fill(id, (byte) 0);
    }
    private void controls(boolean enabled) { register.setEnabled(enabled); account.setEnabled(enabled); pin.setEnabled(enabled); type.setEnabled(enabled); }
    private void cancel() {
        if (client != null) { client.close(); client = null; }
        if (busy) { busy = false; controls(true); status.setText("Registration cancelled."); }
        if (pin != null) pin.setText("");
    }
    @Override public void onPause() { cancel(); super.onPause(); }
}
