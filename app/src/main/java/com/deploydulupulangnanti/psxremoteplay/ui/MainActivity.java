package com.deploydulupulangnanti.psxremoteplay.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.InputDevice;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Button;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.UUID;
import com.deploydulupulangnanti.psxremoteplay.core.*;
import com.deploydulupulangnanti.psxremoteplay.data.AppRepository;
import com.deploydulupulangnanti.psxremoteplay.data.RegistrationStore;
import com.deploydulupulangnanti.psxremoteplay.platform.AndroidController;

/** English UI for the independent Android client. All labels are original, with no reference assets. */
@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private AppRepository repository;
    private final ConsoleDiscovery discovery = new ConsoleDiscovery();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String, DiscoveredConsole> foundConsoles = new LinkedHashMap<>();
    private int searchGeneration;
    private boolean searching, foreground;
    private String searchStatus = "Find a PS4 / PS5 on the same local network, or enter its IP address.";
    private TextView searchStatusView;
    private LinearLayout searchResultsView;
    private Button searchButton, directSearchButton, cancelSearchButton;
    @Override public void onCreate(Bundle state) { super.onCreate(state); repository = new AppRepository(this); }
    @Override public void onResume() { super.onResume(); foreground = true; render(); }
    @Override public void onPause() {
        foreground = false;
        stopSearch("Search stopped when leaving this screen.");
        super.onPause();
    }

    private void render() {
        LinearLayout frame = Ui.column(this); frame.setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); frame.addView(scroll);
        LinearLayout page = Ui.column(this); int pad = Ui.dp(this, 24); page.setPadding(pad, pad, pad, pad); scroll.addView(page);
        setContentView(frame); Ui.insets(frame);
        Ui.text(this, page, "PSX  /  INDEPENDENT LAB", 12, Ui.ACCENT, true);
        Ui.text(this, page, "Remote Play", 34, Ui.TEXT, true);
        Ui.text(this, page, "Find and save your console, then register, wake it up and start a LAN session.", 16, Ui.MUTED, false);

        LinearLayout hero = Ui.card(this, page);
        Ui.text(this, hero, "EXPERIMENTAL CLIENT  /  0.2", 11, Ui.ACCENT, true);
        Ui.text(this, hero, "Explore the local demo", 24, Ui.TEXT, true);
        Ui.text(this, hero, "Explore the session screen and test controller input. This local animation does not connect to a console or play streamed video or audio.", 14, Ui.MUTED, false);
        Ui.button(this, hero, "Open local demo  →", true, view -> startActivity(new Intent(this, StreamActivity.class)));

        StreamProfile selected = repository.selectedProfile();
        LinearLayout profiles = Ui.card(this, page);
        Ui.text(this, profiles, "STREAM PROFILES", 11, Ui.ACCENT, true);
        Ui.text(this, profiles, selected.name, 22, Ui.TEXT, true);
        Ui.text(this, profiles, selected.summary(), 14, Ui.MUTED, false);
        Ui.text(this, profiles, "Resolution, frame rate, bitrate and audio apply to console sessions. Controller dead zone also applies to the local demo.", 12, Ui.MUTED, false);
        Ui.button(this, profiles, "Choose profile", false, view -> chooseProfile());
        Ui.button(this, profiles, "Edit settings", false, view -> editProfile(selected));
        Ui.button(this, profiles, "Create profile", false, view -> editProfile(null));
        if (!selected.id.equals("balanced") && !selected.id.equals("quality")) {
            Ui.button(this, profiles, "Delete profile", false, view -> new AlertDialog.Builder(this)
                    .setMessage("Delete this profile?").setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (dialog, which) -> { repository.deleteProfile(selected.id); render(); }).show());
        }

        renderSearch(page);
        Ui.text(this, page, "Your consoles", 22, Ui.TEXT, true);
        List<Device> devices = repository.devices();
        if (devices.isEmpty()) {
            LinearLayout empty = Ui.card(this, page);
            Ui.text(this, empty, "No consoles saved", 18, Ui.TEXT, true);
            Ui.text(this, empty, "Search the local network or save an address manually. Then open the console menu for registration, wake-up and LAN streaming.", 14, Ui.MUTED, false);
        }
        for (Device device : devices) {
            LinearLayout box = Ui.card(this, page);
            Ui.text(this, box, device.name, 20, Ui.TEXT, true);
            boolean paired = new RegistrationStore(this).has(device.id);
            Ui.text(this, box, device.host + (paired ? "\nPairing credentials saved" : "\nNot registered · address saved locally"), 14, Ui.MUTED, false);
            Ui.button(this, box, "Registration / wake-up / streaming", false, view -> consoleStatus(device));
            Ui.button(this, box, "Edit console", false, view -> editDevice(device));
            Ui.button(this, box, "Delete console", false, view -> new AlertDialog.Builder(this)
                    .setMessage("Delete this console from local storage?").setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (dialog, which) -> {
                        try { new RegistrationStore(this).delete(device.id); repository.deleteDevice(device.id); render(); }
                        catch (IllegalStateException error) { new AlertDialog.Builder(this).setMessage(error.getMessage()).setPositiveButton("Close", null).show(); }
                    }).show());
        }
        Ui.button(this, page, "+ Add console", true, view -> editDevice(null));

        LinearLayout diagnostics = Ui.card(this, page);
        int count = 0;
        for (int id : InputDevice.getDeviceIds()) if (AndroidController.isController(InputDevice.getDevice(id))) count++;
        Ui.text(this, diagnostics, "DIAGNOSTICS", 11, Ui.ACCENT, true);
        Ui.text(this, diagnostics, count + " Android controllers detected", 17, Ui.TEXT, true);
        Ui.text(this, diagnostics, "Pair a gamepad in Android settings, then test its buttons and sticks in the demo. Generic mappings can vary by device.", 14, Ui.MUTED, false);
        Ui.button(this, diagnostics, "View decoder capabilities", false, view -> showCodecs());
        Ui.button(this, diagnostics, "Connection engine licenses and source", false, view -> startActivity(new Intent(this, LicensesActivity.class)));
        Ui.button(this, diagnostics, "Privacy policy", false, view -> startActivity(new Intent(this, PrivacyActivity.class)));
        Ui.text(this, page, "Independent client · Not affiliated with Sony.\nLAN connections use Chiaki. Automatic PSN sign-in and internet connections are not available.", 12, Ui.MUTED, false);
    }
    private void renderSearch(LinearLayout page) {
        LinearLayout card = Ui.card(this, page);
        Ui.text(this, card, "Find your console", 22, Ui.TEXT, true);
        Ui.text(this, card, "Local search takes up to 15 seconds. Enable Remote Play and use the same network. Guest networks or Wi-Fi isolation may block discovery.", 14, Ui.MUTED, false);
        searchStatusView = Ui.text(this, card, searchStatus, 14, Ui.TEXT, false);
        searchStatusView.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        searchButton = Ui.button(this, card, "Search local network", true, view -> startSearch(null));
        directSearchButton = Ui.button(this, card, "Search by IP address", false, view -> directSearchDialog());
        cancelSearchButton = Ui.button(this, card, "Cancel search", false,
                view -> { stopSearch("Search cancelled."); updateSearch(); });
        searchResultsView = Ui.column(this); card.addView(searchResultsView);
        updateSearch();
    }
    private void updateSearch() {
        if (searchStatusView == null) return;
        searchStatusView.setText(searchStatus);
        searchButton.setEnabled(!searching);
        directSearchButton.setEnabled(!searching);
        cancelSearchButton.setVisibility(searching ? android.view.View.VISIBLE : android.view.View.GONE);
        searchResultsView.removeAllViews();
        List<Device> saved = repository.devices();
        for (DiscoveredConsole console : foundConsoles.values()) {
            LinearLayout row = Ui.column(this); searchResultsView.addView(row);
            Ui.text(this, row, console.name, 18, Ui.TEXT, true);
            boolean knownPairing = new RegistrationStore(this).has(repository.deviceIdFor(console));
            Ui.text(this, row, console.type + " · " + console.stateLabel() + "\n" + console.host
                    + (knownPairing ? "\nFound on network · local pairing saved" : "\nFound on network · save to open connection options"), 14, Ui.MUTED, false);
            boolean sameAddress = false;
            boolean sameIdentity = false;
            boolean alreadySaved = false;
            for (Device device : saved) {
                sameAddress |= device.host.equals(console.host);
                sameIdentity |= device.id.equals(repository.deviceIdFor(console));
                alreadySaved |= device.host.equals(console.host)
                        && device.id.equals(repository.deviceIdFor(console));
            }
            Ui.button(this, row, alreadySaved ? "Address already saved"
                    : sameAddress ? "Associate with saved address"
                    : sameIdentity ? "Update saved address" : "Save console address", false, view -> {
                repository.saveDiscoveredConsole(console);
                render();
            }).setEnabled(!alreadySaved);
        }
    }
    private void startSearch(List<ConsoleDiscovery.Target> targets) {
        stopSearch(null);
        final int generation = ++searchGeneration;
        foundConsoles.clear(); searching = true;
        searchStatus = "Searching… 0 consoles found"; updateSearch();
        ConsoleDiscovery.Listener listener = new ConsoleDiscovery.Listener() {
            private void post(Runnable update) {
                mainHandler.post(() -> {
                    if (foreground && searching && generation == searchGeneration && !isFinishing() && !isDestroyed()) {
                        update.run(); updateSearch();
                    }
                });
            }
            @Override public void onFound(DiscoveredConsole console) { post(() -> {
                foundConsoles.put(console.hostId.toLowerCase(Locale.ROOT), console);
                searchStatus = "Searching… " + foundConsoles.size() + " found";
            }); }
            @Override public void onComplete() { post(() -> {
                searching = false;
                searchStatus = foundConsoles.isEmpty()
                        ? "No consoles found. Check your network and Remote Play settings, or try an IP address."
                        : "Search complete · " + foundConsoles.size() + " consoles found. Choose an address to save.";
            }); }
            @Override public void onError(String safeMessage) { post(() -> {
                searching = false; searchStatus = safeMessage;
            }); }
        };
        if (targets == null) discovery.search(listener);
        else discovery.search(targets, 15000, listener);
    }
    private void stopSearch(String message) {
        searchGeneration++;
        discovery.close();
        if (searching && message != null) searchStatus = message;
        searching = false;
    }
    private void directSearchDialog() {
        LinearLayout form = form();
        EditText host = field(form, "Console IPv4 address", "", InputType.TYPE_CLASS_PHONE);
        TextView error = Ui.text(this, form, "", 13, 0xFFFFB4AB, false);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Search by IP")
                .setView(form).setNegativeButton("Cancel", null).setPositiveButton("Search", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                String value = host.getText().toString().trim();
                new Device("validation", "Console", value);
                if (!value.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IllegalArgumentException();
                String[] octets = value.split("\\."); byte[] bytes = new byte[4];
                for (int i = 0; i < 4; i++) bytes[i] = (byte) Integer.parseInt(octets[i]);
                int first = Byte.toUnsignedInt(bytes[0]);
                if (first == 0 || first >= 224) throw new IllegalArgumentException();
                InetAddress address = InetAddress.getByAddress(bytes);
                List<ConsoleDiscovery.Target> targets = new ArrayList<>();
                targets.add(new ConsoleDiscovery.Target(new InetSocketAddress(address, 987), false));
                targets.add(new ConsoleDiscovery.Target(new InetSocketAddress(address, 9302), true));
                dialog.dismiss(); startSearch(targets);
            } catch (IllegalArgumentException | UnknownHostException problem) {
                error.setText("Enter an IPv4 address such as 192.168.1.20, without a URL or port.");
            }
        }));
        dialog.show();
    }
    private void chooseProfile() {
        List<StreamProfile> profiles = repository.profiles(); String[] names = new String[profiles.size()];
        int checked = 0;
        for (int i = 0; i < profiles.size(); i++) {
            names[i] = profiles.get(i).name + " · " + profiles.get(i).height + "p";
            if (profiles.get(i).id.equals(repository.selectedProfile().id)) checked = i;
        }
        new AlertDialog.Builder(this).setTitle("Choose profile").setSingleChoiceItems(names, checked, (dialog, which) -> {
            repository.selectProfile(profiles.get(which).id); dialog.dismiss(); render();
        }).setNegativeButton("Cancel", null).show();
    }
    private LinearLayout form() {
        LinearLayout form = Ui.column(this); int pad = Ui.dp(this, 20); form.setPadding(pad, pad, pad, pad); return form;
    }
    private EditText field(LinearLayout form, String label, String value, int inputType) {
        Ui.text(this, form, label, 13, Ui.MUTED, false);
        EditText field = new EditText(this); field.setText(value); field.setSingleLine(true); field.setInputType(inputType);
        field.setContentDescription(label); form.addView(field); return field;
    }
    private Spinner options(LinearLayout form, String label, String[] values, int selected) {
        Ui.text(this, form, label, 13, Ui.MUTED, false);
        Spinner spinner = new Spinner(this); spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values));
        spinner.setSelection(selected); spinner.setContentDescription(label); form.addView(spinner); return spinner;
    }
    private void editDevice(Device original) {
        LinearLayout form = form();
        EditText name = field(form, "Console name", original == null ? "" : original.name, InputType.TYPE_CLASS_TEXT);
        EditText host = field(form, "IP address / hostname (no URL or port)", original == null ? "" : original.host,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        TextView error = Ui.text(this, form, "", 13, 0xFFFFB4AB, false);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(original == null ? "Add console" : "Edit console")
                .setView(form).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                repository.saveDevice(new Device(original == null ? UUID.randomUUID().toString() : original.id,
                        name.getText().toString(), host.getText().toString())); dialog.dismiss(); render();
            } catch (IllegalArgumentException problem) { error.setText(problem.getMessage()); }
        })); dialog.show();
    }
    private void editProfile(StreamProfile original) {
        StreamProfile current = original == null ? StreamProfile.balanced() : original;
        LinearLayout form = form(); ScrollView scroll = new ScrollView(this); scroll.addView(form);
        EditText name = field(form, "Profile name", original == null ? "" : current.name, InputType.TYPE_CLASS_TEXT);
        Spinner resolution = options(form, "Resolution", new String[]{"720p", "1080p"}, current.height == 720 ? 0 : 1);
        Spinner fps = options(form, "Frame rate", new String[]{"30 fps", "60 fps"}, current.fps == 30 ? 0 : 1);
        EditText bitrate = field(form, "Bitrate (2–50 Mbps)", String.valueOf(current.bitrateMbps), InputType.TYPE_CLASS_NUMBER);
        EditText deadZone = field(form, "Dead zone (0–50 percent)", String.valueOf(Math.round(current.deadZone * 100)), InputType.TYPE_CLASS_NUMBER);
        CheckBox audio = new CheckBox(this); audio.setText("Enable console audio"); audio.setChecked(current.audioEnabled); form.addView(audio);
        TextView error = Ui.text(this, form, "", 13, 0xFFFFB4AB, false);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Profile settings").setView(scroll)
                .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                repository.saveProfile(new StreamProfile(original == null ? UUID.randomUUID().toString() : original.id,
                        name.getText().toString(), resolution.getSelectedItemPosition() == 0 ? 720 : 1080,
                        fps.getSelectedItemPosition() == 0 ? 30 : 60, Integer.parseInt(bitrate.getText().toString()),
                        audio.isChecked(), Integer.parseInt(deadZone.getText().toString()) / 100f));
                dialog.dismiss(); render();
            } catch (IllegalArgumentException problem) { error.setText("Check the name, bitrate (2–50) and dead zone (0–50)."); }
        })); dialog.show();
    }
    private void consoleStatus(Device device) {
        startActivity(new Intent(this, ConsoleActivity.class).putExtra(ConsoleActivity.DEVICE_ID, device.id));
    }
    private void showCodecs() {
        StringBuilder report = new StringBuilder("Device capability inventory; this is not a decoding test.\n\n");
        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (type.equals("video/avc") || type.equals("video/hevc")) {
                        report.append(info.getName()).append("\n").append(type);
                        if (Build.VERSION.SDK_INT >= 29) report.append(info.isHardwareAccelerated() ? " · hardware" : " · software/vendor");
                        else report.append(" · acceleration unknown");
                        report.append("\n\n");
                    }
                }
            }
        } catch (RuntimeException error) { report.append("Unable to read codec inventory."); }
        TextView content = new TextView(this); content.setText(report); int pad = Ui.dp(this, 20); content.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        new AlertDialog.Builder(this).setTitle("Android decoders").setView(scroll).setPositiveButton("Close", null).show();
    }
}
