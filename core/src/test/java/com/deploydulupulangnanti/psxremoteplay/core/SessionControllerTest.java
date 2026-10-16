package com.deploydulupulangnanti.psxremoteplay.core;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class SessionControllerTest {
    private final Device device = new Device("test", "Lab", "192.168.1.10");
    @Test public void demoIsNeverReportedAsRealStreaming() {
        List<SessionController.State> events = new ArrayList<>();
        SessionController session = new SessionController(new DemoTransport(), (state, message) -> events.add(state));
        session.start(device, StreamProfile.balanced());
        assertEquals(SessionController.State.DEMO_ACTIVE, session.getState());
        assertFalse(events.contains(SessionController.State.STREAMING));
        session.close();
        assertEquals(SessionController.State.STOPPED, session.getState());
    }
    @Test public void consoleStubReturnsExplicitFailure() {
        SessionController session = new SessionController(new ConsoleTransport(), (state, message) -> {
            if (state == SessionController.State.ERROR) assertEquals(ConsoleTransport.UNAVAILABLE, message);
        });
        session.start(device, StreamProfile.balanced());
        assertEquals(SessionController.State.ERROR, session.getState());
    }
    @Test public void lateCallbacksCannotReviveStoppedOrNewSession() {
        Deferred transport = new Deferred();
        SessionController session = new SessionController(transport, (state, message) -> { });
        session.start(device, StreamProfile.balanced());
        SessionTransport.Listener old = transport.listener;
        session.close();
        old.onReady();
        assertEquals(SessionController.State.STOPPED, session.getState());
        session.start(device, StreamProfile.balanced());
        old.onFailure("late");
        assertEquals(SessionController.State.CONNECTING, session.getState());
        transport.listener.onReady();
        assertEquals(SessionController.State.STREAMING, session.getState());
    }
    @Test public void inputFailureClosesTransport() {
        Deferred transport = new Deferred();
        SessionController session = new SessionController(transport, (state, message) -> { });
        session.start(device, StreamProfile.balanced()); transport.listener.onReady();
        transport.failInput = true;
        session.sendInput(ControllerInput.Snapshot.neutral());
        assertTrue(transport.closed);
        assertEquals(SessionController.State.ERROR, session.getState());
    }
    private static final class Deferred implements SessionTransport {
        Listener listener; boolean closed, failInput;
        public boolean isDemo() { return false; }
        public void open(Device device, StreamProfile profile, Listener listener) { this.listener = listener; closed = false; }
        public void sendInput(ControllerInput.Snapshot input) { if (failInput) throw new IllegalStateException(); }
        public void close() { closed = true; }
    }
}
