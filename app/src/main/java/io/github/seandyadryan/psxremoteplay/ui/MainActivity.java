package io.github.seandyadryan.psxremoteplay.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.InputDevice;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.util.List;
import java.util.UUID;
import io.github.seandyadryan.psxremoteplay.core.*;
import io.github.seandyadryan.psxremoteplay.data.AppRepository;
import io.github.seandyadryan.psxremoteplay.platform.AndroidController;

/** Indonesian-only prototype UI. All labels are original, with no reference assets. */
@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private AppRepository repository;
    @Override public void onCreate(Bundle state) { super.onCreate(state); repository = new AppRepository(this); }
    @Override public void onResume() { super.onResume(); render(); }

    private void render() {
        LinearLayout frame = Ui.column(this); frame.setBackgroundColor(Ui.BG);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); frame.addView(scroll);
        LinearLayout page = Ui.column(this); int pad = Ui.dp(this, 24); page.setPadding(pad, pad, pad, pad); scroll.addView(page);
        setContentView(frame); Ui.insets(frame);
        Ui.text(this, page, "PSX  /  INDEPENDENT LAB", 12, Ui.ACCENT, true);
        Ui.text(this, page, "Remote Play", 34, Ui.TEXT, true);
        Ui.text(this, page, "Ruang bermain, dalam jangkauan.", 16, Ui.MUTED, false);

        LinearLayout hero = Ui.card(this, page);
        Ui.text(this, hero, "PROTOTYPE  /  0.1", 11, Ui.ACCENT, true);
        Ui.text(this, hero, "Mulai dari demo lokal", 24, Ui.TEXT, true);
        Ui.text(this, hero, "Jelajahi tampilan sesi dan uji input controller. Demo membuat animasi lokal; tidak terhubung ke konsol dan tidak memutar audio/video streaming.", 14, Ui.MUTED, false);
        Ui.button(this, hero, "Buka demo lokal  →", true, view -> startActivity(new Intent(this, StreamActivity.class)));

        StreamProfile selected = repository.selectedProfile();
        LinearLayout profiles = Ui.card(this, page);
        Ui.text(this, profiles, "PROFIL STREAMING", 11, Ui.ACCENT, true);
        Ui.text(this, profiles, selected.name, 22, Ui.TEXT, true);
        Ui.text(this, profiles, selected.summary(), 14, Ui.MUTED, false);
        Ui.text(this, profiles, "Parameter tersimpan untuk adapter mendatang. Resolusi, FPS, bitrate dan audio belum diterapkan pada stream nyata; dead zone digunakan di demo.", 12, Ui.MUTED, false);
        Ui.button(this, profiles, "Pilih profil", false, view -> chooseProfile());
        Ui.button(this, profiles, "Ubah pengaturan", false, view -> editProfile(selected));
        Ui.button(this, profiles, "Buat profil baru", false, view -> editProfile(null));
        if (!selected.id.equals("balanced") && !selected.id.equals("quality")) {
            Ui.button(this, profiles, "Hapus profil", false, view -> new AlertDialog.Builder(this)
                    .setMessage("Hapus profil ini?").setNegativeButton("Batal", null)
                    .setPositiveButton("Hapus", (dialog, which) -> { repository.deleteProfile(selected.id); render(); }).show());
        }

        Ui.text(this, page, "Perangkat Anda", 22, Ui.TEXT, true);
        List<Device> devices = repository.devices();
        if (devices.isEmpty()) {
            LinearLayout empty = Ui.card(this, page);
            Ui.text(this, empty, "Belum ada perangkat", 18, Ui.TEXT, true);
            Ui.text(this, empty, "Simpan nama dan alamat perangkat sebagai buku alamat lokal. Registrasi, discovery dan koneksi konsol belum tersedia.", 14, Ui.MUTED, false);
        }
        for (Device device : devices) {
            LinearLayout box = Ui.card(this, page);
            Ui.text(this, box, device.name, 20, Ui.TEXT, true);
            Ui.text(this, box, device.host + "\nBelum terdaftar · alamat lokal tersimpan", 14, Ui.MUTED, false);
            Ui.button(this, box, "Status koneksi konsol", false, view -> consoleStatus(device));
            Ui.button(this, box, "Ubah perangkat", false, view -> editDevice(device));
            Ui.button(this, box, "Hapus perangkat", false, view -> new AlertDialog.Builder(this)
                    .setMessage("Hapus perangkat dari penyimpanan lokal?").setNegativeButton("Batal", null)
                    .setPositiveButton("Hapus", (dialog, which) -> { repository.deleteDevice(device.id); render(); }).show());
        }
        Ui.button(this, page, "+ Tambah perangkat", true, view -> editDevice(null));

        LinearLayout diagnostics = Ui.card(this, page);
        int count = 0;
        for (int id : InputDevice.getDeviceIds()) if (AndroidController.isController(InputDevice.getDevice(id))) count++;
        Ui.text(this, diagnostics, "DIAGNOSTIK", 11, Ui.ACCENT, true);
        Ui.text(this, diagnostics, count + " controller Android terdeteksi", 17, Ui.TEXT, true);
        Ui.text(this, diagnostics, "Pasangkan gamepad melalui Android, lalu uji tombol dan stick di demo. Mapping generik dapat berbeda antarperangkat.", 14, Ui.MUTED, false);
        Ui.button(this, diagnostics, "Lihat kemampuan decoder", false, view -> showCodecs());
        Ui.text(this, page, "Implementasi independen · Tanpa afiliasi Sony atau PXPlay.\nTidak ada protokol konsol, login akun, DRM atau bypass lisensi.", 12, Ui.MUTED, false);
    }
    private void chooseProfile() {
        List<StreamProfile> profiles = repository.profiles(); String[] names = new String[profiles.size()];
        int checked = 0;
        for (int i = 0; i < profiles.size(); i++) {
            names[i] = profiles.get(i).name + " · " + profiles.get(i).height + "p";
            if (profiles.get(i).id.equals(repository.selectedProfile().id)) checked = i;
        }
        new AlertDialog.Builder(this).setTitle("Pilih profil").setSingleChoiceItems(names, checked, (dialog, which) -> {
            repository.selectProfile(profiles.get(which).id); dialog.dismiss(); render();
        }).setNegativeButton("Batal", null).show();
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
        EditText name = field(form, "Nama perangkat", original == null ? "" : original.name, InputType.TYPE_CLASS_TEXT);
        EditText host = field(form, "Alamat IP / hostname (tanpa URL atau port)", original == null ? "" : original.host,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        TextView error = Ui.text(this, form, "", 13, 0xFFFFB4AB, false);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(original == null ? "Tambah perangkat" : "Ubah perangkat")
                .setView(form).setNegativeButton("Batal", null).setPositiveButton("Simpan", null).create();
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
        EditText name = field(form, "Nama profil", original == null ? "" : current.name, InputType.TYPE_CLASS_TEXT);
        Spinner resolution = options(form, "Resolusi", new String[]{"720p", "1080p"}, current.height == 720 ? 0 : 1);
        Spinner fps = options(form, "Frame rate", new String[]{"30 fps", "60 fps"}, current.fps == 30 ? 0 : 1);
        EditText bitrate = field(form, "Bitrate (2–50 Mbps)", String.valueOf(current.bitrateMbps), InputType.TYPE_CLASS_NUMBER);
        EditText deadZone = field(form, "Dead zone (0–50 persen)", String.valueOf(Math.round(current.deadZone * 100)), InputType.TYPE_CLASS_NUMBER);
        CheckBox audio = new CheckBox(this); audio.setText("Audio aktif (konfigurasi untuk stream mendatang)"); audio.setChecked(current.audioEnabled); form.addView(audio);
        TextView error = Ui.text(this, form, "", 13, 0xFFFFB4AB, false);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Pengaturan profil").setView(scroll)
                .setNegativeButton("Batal", null).setPositiveButton("Simpan", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                repository.saveProfile(new StreamProfile(original == null ? UUID.randomUUID().toString() : original.id,
                        name.getText().toString(), resolution.getSelectedItemPosition() == 0 ? 720 : 1080,
                        fps.getSelectedItemPosition() == 0 ? 30 : 60, Integer.parseInt(bitrate.getText().toString()),
                        audio.isChecked(), Integer.parseInt(deadZone.getText().toString()) / 100f));
                dialog.dismiss(); render();
            } catch (IllegalArgumentException problem) { error.setText("Periksa nama, bitrate 2–50, dan dead zone 0–50."); }
        })); dialog.show();
    }
    private void consoleStatus(Device device) {
        SessionController session = new SessionController(new ConsoleTransport(), (state, message) -> {
            if (state == SessionController.State.ERROR) new AlertDialog.Builder(this)
                    .setTitle("Koneksi belum tersedia").setMessage(message).setPositiveButton("Mengerti", null).show();
        });
        session.start(device, repository.selectedProfile()); session.close();
    }
    private void showCodecs() {
        StringBuilder report = new StringBuilder("Inventaris kemampuan perangkat, bukan uji decoding.\n\n");
        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (type.equals("video/avc") || type.equals("video/hevc")) {
                        report.append(info.getName()).append("\n").append(type);
                        if (Build.VERSION.SDK_INT >= 29) report.append(info.isHardwareAccelerated() ? " · hardware" : " · software/vendor");
                        else report.append(" · akselerasi belum diketahui");
                        report.append("\n\n");
                    }
                }
            }
        } catch (RuntimeException error) { report.append("Inventaris codec gagal dibaca."); }
        TextView content = new TextView(this); content.setText(report); int pad = Ui.dp(this, 20); content.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this); scroll.addView(content);
        new AlertDialog.Builder(this).setTitle("Decoder Android").setView(scroll).setPositiveButton("Tutup", null).show();
    }
}
