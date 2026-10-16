package io.github.seandyadryan.psxremoteplay.core;

public final class StreamProfile {
    public final String id;
    public final String name;
    public final int height;
    public final int fps;
    public final int bitrateMbps;
    public final boolean audioEnabled;
    public final float deadZone;

    public StreamProfile(String id, String name, int height, int fps, int bitrateMbps,
                         boolean audioEnabled, float deadZone) {
        this.id = Device.requireText(id, "ID", 80);
        this.name = Device.requireText(name, "Nama profil", 60);
        if (height != 720 && height != 1080) throw new IllegalArgumentException("Resolusi harus 720p/1080p.");
        if (fps != 30 && fps != 60) throw new IllegalArgumentException("FPS harus 30/60.");
        if (bitrateMbps < 2 || bitrateMbps > 50) throw new IllegalArgumentException("Bitrate harus 2–50 Mbps.");
        if (!Float.isFinite(deadZone) || deadZone < 0 || deadZone > 0.5f) {
            throw new IllegalArgumentException("Dead zone harus 0–0,5.");
        }
        this.height = height;
        this.fps = fps;
        this.bitrateMbps = bitrateMbps;
        this.audioEnabled = audioEnabled;
        this.deadZone = deadZone;
    }

    public static StreamProfile balanced() {
        return new StreamProfile("balanced", "Balanced", 720, 60, 12, true, 0.12f);
    }

    public String summary() {
        return height + "p · " + fps + " fps · " + bitrateMbps + " Mbps · Audio " + (audioEnabled ? "on" : "off");
    }
}
