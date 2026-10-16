package com.deploydulupulangnanti.psxremoteplay.core;

/** Local UI simulation; performs no networking, decoding or audio playback. */
public final class DemoTransport implements SessionTransport {
    private boolean open;
    @Override public boolean isDemo() { return true; }
    @Override public void open(Device device, StreamProfile profile, Listener listener) {
        open = true;
        listener.onReady();
    }
    @Override public void sendInput(ControllerInput.Snapshot input) {
        if (!open) throw new IllegalStateException("Demo belum aktif.");
    }
    @Override public void close() { open = false; }
}
