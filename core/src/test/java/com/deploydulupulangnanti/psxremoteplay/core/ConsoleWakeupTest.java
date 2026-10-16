package com.deploydulupulangnanti.psxremoteplay.core;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConsoleWakeupTest {
    @Test public void sendsExactlyThreeCorrectPacketsThenReportsOnlyTransmission() throws Exception {
        try (DatagramSocket endpoint = endpoint(); ConsoleWakeup wakeup = new ConsoleWakeup()) {
            Recorder listener = new Recorder();
            ConsoleRegistration registration = ConnectionPrimitivesTest.registration(true, "1a2b3c4d");
            wakeup.send((InetSocketAddress) endpoint.getLocalSocketAddress(), registration, listener);
            for (int i = 0; i < 3; i++) assertArrayEquals(WakeupProtocol.request(registration), receive(endpoint));
            assertTrue(listener.done.await(2, TimeUnit.SECONDS));
            assertEquals(1, listener.sent);
            assertNull(listener.error);
            assertNotSame(Thread.currentThread(), listener.thread);
            endpoint.setSoTimeout(350);
            assertThrows(SocketTimeoutException.class, () -> receive(endpoint));
        }
    }

    @Test public void closeSuppressesRetriesAndCallbackAndAllowsAnotherSend() throws Exception {
        try (DatagramSocket first = endpoint(); DatagramSocket second = endpoint(); ConsoleWakeup wakeup = new ConsoleWakeup()) {
            Recorder cancelled = new Recorder();
            ConsoleRegistration registration = ConnectionPrimitivesTest.registration(false, "abc123");
            wakeup.send((InetSocketAddress) first.getLocalSocketAddress(), registration, cancelled);
            receive(first);
            wakeup.close();
            first.setSoTimeout(600);
            assertThrows(SocketTimeoutException.class, () -> receive(first));
            assertEquals(0, cancelled.sent);
            assertNull(cancelled.error);
            Recorder replacement = new Recorder();
            wakeup.send((InetSocketAddress) second.getLocalSocketAddress(), registration, replacement);
            for (int i = 0; i < 3; i++) receive(second);
            assertTrue(replacement.done.await(2, TimeUnit.SECONDS));
            assertEquals(1, replacement.sent);
            assertNull(replacement.error);
        }
    }

    @Test public void newSendReplacesOldRunWithoutStaleCompletion() throws Exception {
        try (DatagramSocket first = endpoint(); DatagramSocket second = endpoint(); ConsoleWakeup wakeup = new ConsoleWakeup()) {
            Recorder old = new Recorder();
            Recorder replacement = new Recorder();
            ConsoleRegistration registration = ConnectionPrimitivesTest.registration(true, "a");
            wakeup.send((InetSocketAddress) first.getLocalSocketAddress(), registration, old);
            receive(first);
            wakeup.send((InetSocketAddress) second.getLocalSocketAddress(), registration, replacement);
            for (int i = 0; i < 3; i++) receive(second);
            assertTrue(replacement.done.await(2, TimeUnit.SECONDS));
            assertEquals(1, replacement.sent);
            assertEquals(0, old.sent);
            assertNull(old.error);
        }
    }

    @Test public void rejectsMalformedHostAndUnusableEndpointsBeforeStartingWorker() throws Exception {
        try (ConsoleWakeup wakeup = new ConsoleWakeup()) {
            ConsoleRegistration registration = ConnectionPrimitivesTest.registration(true, "abc");
            for (String host : new String[]{"http://localhost", "127.0.0.1:9302", "127.0.0.999", "example.org/path"})
                assertThrows(IllegalArgumentException.class, () -> wakeup.send(host, registration, new Recorder()));
            assertThrows(IllegalArgumentException.class, () -> wakeup.send(InetSocketAddress.createUnresolved("x", 9000), registration, new Recorder()));
            assertThrows(IllegalArgumentException.class, () -> wakeup.send(new InetSocketAddress("127.0.0.1", 0), registration, new Recorder()));
        }
    }

    private static DatagramSocket endpoint() throws Exception {
        DatagramSocket socket = new DatagramSocket(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
        socket.setSoTimeout(2000);
        return socket;
    }

    private static byte[] receive(DatagramSocket socket) throws Exception {
        DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
        socket.receive(packet);
        return java.util.Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getOffset() + packet.getLength());
    }

    private static final class Recorder implements ConsoleWakeup.Listener {
        final CountDownLatch done = new CountDownLatch(1);
        volatile int sent;
        volatile String error;
        volatile Thread thread;
        @Override public void onSent() { thread = Thread.currentThread(); sent++; done.countDown(); }
        @Override public void onFailure(String message) { error = message; done.countDown(); }
    }
}
