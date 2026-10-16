package io.github.seandyadryan.psxremoteplay.core;

import java.util.Objects;

/** Single-threaded lifecycle with generation guards for late transport callbacks. */
public final class SessionController implements AutoCloseable {
    public enum State { IDLE, CONNECTING, DEMO_ACTIVE, STREAMING, STOPPED, ERROR }
    public interface Observer { void onState(State state, String safeMessage); }
    private final SessionTransport transport;
    private final Observer observer;
    private State state = State.IDLE;
    private long generation;

    public SessionController(SessionTransport transport, Observer observer) {
        this.transport = Objects.requireNonNull(transport);
        this.observer = Objects.requireNonNull(observer);
    }
    public State getState() { return state; }
    public void start(Device device, StreamProfile profile) {
        Objects.requireNonNull(device); Objects.requireNonNull(profile);
        if (state == State.CONNECTING || state == State.DEMO_ACTIVE || state == State.STREAMING) {
            throw new IllegalStateException("Sesi sudah aktif.");
        }
        long attempt = ++generation;
        set(State.CONNECTING, "Menyiapkan sesi");
        try {
            transport.open(device, profile, new SessionTransport.Listener() {
                @Override public void onReady() {
                    if (attempt == generation && state == State.CONNECTING) {
                        set(transport.isDemo() ? State.DEMO_ACTIVE : State.STREAMING,
                                transport.isDemo() ? "Demo lokal — bukan streaming konsol" : "Streaming aktif");
                    }
                }
                @Override public void onFailure(String message) {
                    if (attempt == generation && state != State.ERROR) fail(message);
                }
            });
        } catch (RuntimeException error) {
            if (attempt == generation) fail("Sesi gagal dibuka. Periksa implementasi transport.");
        }
    }
    public void sendInput(ControllerInput.Snapshot input) {
        if (state == State.DEMO_ACTIVE || state == State.STREAMING) {
            try { transport.sendInput(input); }
            catch (RuntimeException error) { fail("Pengiriman input gagal."); }
        }
    }
    private void fail(String message) {
        ++generation;
        try { transport.close(); }
        finally { set(State.ERROR, message); }
    }
    @Override public void close() {
        ++generation;
        try { transport.close(); }
        finally { set(State.STOPPED, "Sesi dihentikan"); }
    }
    private void set(State value, String message) { state = value; observer.onState(value, message); }
}
