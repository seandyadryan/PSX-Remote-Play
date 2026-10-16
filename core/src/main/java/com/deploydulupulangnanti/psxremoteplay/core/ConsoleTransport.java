package com.deploydulupulangnanti.psxremoteplay.core;

/** Deliberately unavailable until a lawful, reviewed protocol implementation is integrated. */
public final class ConsoleTransport implements SessionTransport {
    public static final String UNAVAILABLE = "Koneksi konsol belum diimplementasikan. Registrasi, autentikasi dan protokol streaming belum tersedia. Gunakan demo lokal.";
    @Override public boolean isDemo() { return false; }
    @Override public void open(Device device, StreamProfile profile, Listener listener) {
        listener.onFailure(UNAVAILABLE);
    }
    @Override public void sendInput(ControllerInput.Snapshot input) { }
    @Override public void close() { }
}
