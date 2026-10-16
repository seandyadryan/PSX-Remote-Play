package io.github.seandyadryan.psxremoteplay.core;

/** All methods/callbacks must run on the session owner's thread (Android main thread). */
public interface SessionTransport extends AutoCloseable {
    interface Listener {
        void onReady();
        void onFailure(String safeMessage);
    }
    boolean isDemo();
    void open(Device device, StreamProfile profile, Listener listener);
    void sendInput(ControllerInput.Snapshot input);
    @Override void close();
}
