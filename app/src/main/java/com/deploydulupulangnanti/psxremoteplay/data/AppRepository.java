package com.deploydulupulangnanti.psxremoteplay.data;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import com.deploydulupulangnanti.psxremoteplay.core.Device;
import com.deploydulupulangnanti.psxremoteplay.core.DiscoveredConsole;
import com.deploydulupulangnanti.psxremoteplay.core.StreamProfile;
import com.deploydulupulangnanti.psxremoteplay.platform.AppLog;

/** Versioned, app-private local preferences. No credentials are accepted or stored. */
public final class AppRepository {
    private final SharedPreferences prefs;
    public AppRepository(Context context) { prefs = context.getSharedPreferences("psx_local_v1", Context.MODE_PRIVATE); }
    public List<Device> devices() {
        List<Device> result = new ArrayList<>();
        JSONArray rows = read("devices");
        for (int i = 0; i < rows.length(); i++) {
            try {
                JSONObject row = rows.getJSONObject(i);
                result.add(new Device(row.getString("id"), row.getString("name"), row.getString("host")));
            } catch (JSONException | IllegalArgumentException error) { AppLog.event("invalid_device_record"); }
        }
        return result;
    }
    public void saveDevice(Device device) {
        List<Device> all = devices();
        all.removeIf(item -> item.id.equals(device.id)); all.add(device);
        writeDevices(all);
    }
    private void writeDevices(List<Device> all) {
        writeDevices(all, null, null);
    }
    private void writeDevices(List<Device> all, String identity, String deviceId) {
        JSONArray rows = new JSONArray();
        for (Device item : all) {
            try { rows.put(new JSONObject().put("id", item.id).put("name", item.name).put("host", item.host)); }
            catch (JSONException error) { throw new IllegalStateException("Penyimpanan perangkat gagal.", error); }
        }
        SharedPreferences.Editor editor = prefs.edit().putString("devices", rows.toString());
        if (identity != null) editor.putString("discovery-id:" + identity, deviceId);
        editor.apply();
    }
    /** Saves an address, never registration credentials; keeps an existing custom name. */
    public Device saveDiscoveredConsole(DiscoveredConsole console) {
        String identity = discoveredDeviceId(console);
        String id = deviceIdFor(console);
        List<Device> saved = devices();
        Device existing = null;
        for (Device item : saved) if (item.id.equals(id)) { existing = item; break; }
        if (existing == null) {
            for (Device item : saved) if (item.host.equals(console.host)) { existing = item; break; }
        }
        String selectedId = existing == null ? id : existing.id;
        Device device = new Device(selectedId,
                existing == null ? console.name : existing.name, console.host);
        // Preserve IDs (and their pairing credentials) while remembering the discovered identity.
        saved.removeIf(item -> item.id.equals(selectedId) || item.host.equals(console.host));
        saved.add(device);
        writeDevices(saved, identity, selectedId);
        return device;
    }
    public static String discoveredDeviceId(DiscoveredConsole console) {
        return UUID.nameUUIDFromBytes(("discovery:" + console.hostId.toLowerCase(Locale.ROOT))
                .getBytes(StandardCharsets.UTF_8)).toString();
    }
    public String deviceIdFor(DiscoveredConsole console) {
        String identity = discoveredDeviceId(console);
        return prefs.getString("discovery-id:" + identity, identity);
    }
    public void deleteDevice(String id) {
        JSONArray rows = new JSONArray();
        for (Device item : devices()) {
            if (!item.id.equals(id)) {
                try { rows.put(new JSONObject().put("id", item.id).put("name", item.name).put("host", item.host)); }
                catch (JSONException error) { throw new IllegalStateException(error); }
            }
        }
        SharedPreferences.Editor editor = prefs.edit().putString("devices", rows.toString());
        for (java.util.Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getKey().startsWith("discovery-id:") && id.equals(entry.getValue())) editor.remove(entry.getKey());
        }
        editor.apply();
    }
    public List<StreamProfile> profiles() {
        List<StreamProfile> result = new ArrayList<>();
        result.add(StreamProfile.balanced());
        result.add(new StreamProfile("quality", "Quality", 1080, 60, 25, true, 0.12f));
        JSONArray rows = read("profiles");
        for (int i = 0; i < rows.length(); i++) {
            try {
                JSONObject row = rows.getJSONObject(i);
                StreamProfile profile = new StreamProfile(row.getString("id"), row.getString("name"),
                        row.getInt("height"), row.getInt("fps"), row.getInt("bitrate"),
                        row.getBoolean("audio"), (float) row.getDouble("deadZone"));
                result.removeIf(item -> item.id.equals(profile.id)); result.add(profile);
            } catch (JSONException | IllegalArgumentException error) { AppLog.event("invalid_profile_record"); }
        }
        return result;
    }
    public StreamProfile selectedProfile() {
        String id = prefs.getString("selectedProfile", "balanced");
        for (StreamProfile item : profiles()) if (item.id.equals(id)) return item;
        return StreamProfile.balanced();
    }
    public void selectProfile(String id) { prefs.edit().putString("selectedProfile", id).apply(); }
    public void saveProfile(StreamProfile profile) {
        JSONArray rows = new JSONArray();
        for (StreamProfile item : profiles()) {
            if (item.id.equals(profile.id)) continue;
            rows.put(encode(item));
        }
        rows.put(encode(profile));
        prefs.edit().putString("profiles", rows.toString()).putString("selectedProfile", profile.id).apply();
    }
    public void deleteProfile(String id) {
        JSONArray rows = new JSONArray();
        for (StreamProfile item : profiles()) if (!item.id.equals(id)) rows.put(encode(item));
        prefs.edit().putString("profiles", rows.toString()).putString("selectedProfile", "balanced").apply();
    }
    private JSONObject encode(StreamProfile profile) {
        try {
            return new JSONObject().put("id", profile.id).put("name", profile.name).put("height", profile.height)
                    .put("fps", profile.fps).put("bitrate", profile.bitrateMbps)
                    .put("audio", profile.audioEnabled).put("deadZone", profile.deadZone);
        } catch (JSONException error) { throw new IllegalStateException("Penyimpanan profil gagal.", error); }
    }
    private JSONArray read(String key) {
        try { return new JSONArray(prefs.getString(key, "[]")); }
        catch (JSONException error) { AppLog.event("invalid_storage_json"); return new JSONArray(); }
    }
}
