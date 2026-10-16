package com.deploydulupulangnanti.psxremoteplay.connection;

/** JNI boundary to the source-built, version-pinned Chiaki protocol library. */
public final class NativeBridge {
    private static final boolean AVAILABLE;
    static {
        boolean loaded;
        try { System.loadLibrary("psx-connection"); loaded = true; }
        catch (UnsatisfiedLinkError error) { loaded = false; }
        AVAILABLE = loaded;
    }
    private NativeBridge() { }
    public static boolean available() { return AVAILABLE; }
    public interface RegistrationCallback {
        void onSuccess(boolean ps5, String name, byte[] mac, byte[] registrationKey, byte[] morning);
        void onFailure();
    }
    public interface SessionCallback {
        void onConnected();
        void onLoginPin(boolean incorrect);
        void onQuit(int reason);
        boolean onVideo(byte[] annexB);
        void onAudioSettings(int sampleRate, int channels);
        void onAudio(short[] pcm);
    }
    public static native String libraryVersion();
    public static native long register(String host, boolean ps5, byte[] accountId, int pin, RegistrationCallback callback);
    public static native void stopRegistration(long handle);
    public static native long startSession(String host, boolean ps5, byte[] registrationKey, byte[] morning,
            int width, int height, int fps, int bitrateKbps, boolean audio, SessionCallback callback);
    public static native void stopSession(long handle);
    public static native void setController(long handle, int buttons, float lx, float ly, float rx, float ry, float lt, float rt);
    public static native void setLoginPin(long handle, String pin);
}
