package com.deploydulupulangnanti.psxremoteplay.core;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConsoleDiscoveryTest {
    private static final String RESPONSE = "HTTP/1.1 200 OK\nhost-id:abcdef123456\nhost-name:Lab\nhost-type:PS5\n";

    @Test public void loopbackSearchFindsUpdatesOnceAndSelectsBothProtocolVersions() throws Exception {
        try (DatagramSocket ps4 = endpoint(); DatagramSocket ps5 = endpoint(); ConsoleDiscovery discovery = new ConsoleDiscovery()) {
            Recorder listener = new Recorder();
            discovery.search(List.of(target(ps4, false), target(ps5, true)), 700, listener);
            DatagramPacket first = receive(ps4);
            DatagramPacket second = receive(ps5);
            assertTrue(text(first).contains(":00020020\r\n"));
            assertTrue(text(second).contains(":00030010\r\n"));
            send(ps5, second, RESPONSE);
            send(ps5, second, RESPONSE);
            send(ps5, second, RESPONSE.replace("200 OK", "620 Server Standby"));
            assertTrue(listener.done.await(3, TimeUnit.SECONDS));
            assertNull(listener.error);
            assertEquals(1, listener.completions);
            assertEquals(2, listener.found.size());
            assertEquals("127.0.0.1", listener.found.get(0).host);
            assertEquals(620, listener.found.get(1).statusCode);
            assertNotSame(Thread.currentThread(), listener.callbackThread);
        }
    }

    @Test public void continuousDuplicatesAndJunkCannotExtendDeadlineAndSearchRepeatsRequests() throws Exception {
        try (DatagramSocket fake = endpoint(); ConsoleDiscovery discovery = new ConsoleDiscovery()) {
            Recorder listener = new Recorder();
            long start = System.nanoTime();
            discovery.search(List.of(target(fake, true)), 600, listener);
            DatagramPacket request = receive(fake);
            AtomicReference<Throwable> senderError = new AtomicReference<>();
            Thread flood = new Thread(() -> {
                long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(900);
                try {
                    while (System.nanoTime() < end && listener.done.getCount() != 0) {
                        send(fake, request, "not a console\n");
                        send(fake, request, RESPONSE);
                        Thread.sleep(1);
                    }
                } catch (Exception error) { senderError.set(error); }
            });
            flood.start();
            assertTrue(listener.done.await(2, TimeUnit.SECONDS));
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue("Deadline extended by junk: " + elapsed, elapsed < 1800);
            assertTrue("Finished before the configured deadline", elapsed >= 500);
            assertEquals(1, listener.found.size());
            assertNull(listener.error);
            assertTrue(text(receive(fake)).startsWith("SRCH * HTTP/1.1"));
            flood.join(2000);
            assertFalse(flood.isAlive());
            assertNull(senderError.get());
        }
    }

    @Test public void closeUnblocksReceiveAndSuppressesLateResultsAndCompletion() throws Exception {
        try (DatagramSocket fake = endpoint(); ConsoleDiscovery discovery = new ConsoleDiscovery()) {
            Recorder cancelled = new Recorder();
            discovery.search(List.of(target(fake, true)), 1000, cancelled);
            DatagramPacket oldRequest = receive(fake);
            discovery.close();
            send(fake, oldRequest, RESPONSE);
            Recorder restarted = new Recorder();
            discovery.search(List.of(target(fake, true)), 350, restarted);
            DatagramPacket newRequest = receive(fake);
            send(fake, newRequest, RESPONSE);
            assertTrue(restarted.done.await(2, TimeUnit.SECONDS));
            assertEquals(1, restarted.found.size());
            assertEquals(0, cancelled.completions);
            assertNull(cancelled.error);
            assertTrue(cancelled.found.isEmpty());
        }
    }

    @Test public void startingNewSearchCancelsPreviousRunWithoutStaleCompletion() throws Exception {
        try (DatagramSocket oldEndpoint = endpoint(); DatagramSocket newEndpoint = endpoint(); ConsoleDiscovery discovery = new ConsoleDiscovery()) {
            // This checks replacement/callback ownership, not sub-second thread scheduling.
            // Native compilation and emulator startup can briefly saturate the Windows host.
            oldEndpoint.setSoTimeout(8000); newEndpoint.setSoTimeout(8000);
            Recorder previous = new Recorder();
            discovery.search(List.of(target(oldEndpoint, false)), 15000, previous);
            DatagramPacket oldRequest = receive(oldEndpoint);
            Recorder replacement = new Recorder();
            discovery.search(List.of(target(newEndpoint, true)), 3000, replacement);
            DatagramPacket newRequest = receive(newEndpoint);
            send(oldEndpoint, oldRequest, RESPONSE);
            send(newEndpoint, newRequest, RESPONSE.replace("Lab", "Replacement"));
            assertTrue(replacement.done.await(8, TimeUnit.SECONDS));
            assertEquals(1, replacement.found.size());
            assertEquals("Replacement", replacement.found.get(0).name);
            assertTrue(previous.found.isEmpty());
            assertEquals(0, previous.completions);
            assertNull(previous.error);
        }
    }

    @Test public void silenceFinishesNormallyWithoutInventingAConsole() throws Exception {
        try (DatagramSocket fake = endpoint(); ConsoleDiscovery discovery = new ConsoleDiscovery()) {
            Recorder listener = new Recorder();
            discovery.search(List.of(target(fake, false)), 150, listener);
            receive(fake);
            assertTrue(listener.done.await(2, TimeUnit.SECONDS));
            assertTrue(listener.found.isEmpty());
            assertEquals(1, listener.completions);
            assertNull(listener.error);
            fake.setSoTimeout(100);
            try { receive(fake); fail("Search transmitted after completion"); }
            catch (SocketTimeoutException expected) { }
        }
    }

    @Test public void rejectsUnboundedSearchConfiguration() throws Exception {
        try (DatagramSocket fake = endpoint(); ConsoleDiscovery discovery = new ConsoleDiscovery()) {
            for (int duration : new int[]{0, -1, 15_001}) {
                try { discovery.search(List.of(target(fake, false)), duration, new Recorder()); fail("Accepted unbounded duration"); }
                catch (IllegalArgumentException expected) { }
            }
            try { discovery.search(List.of(), 100, new Recorder()); fail("Accepted empty targets"); }
            catch (IllegalArgumentException expected) { }
        }
    }

    private static DatagramSocket endpoint() throws Exception {
        DatagramSocket socket = new DatagramSocket(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
        socket.setSoTimeout(2000);
        return socket;
    }
    private static ConsoleDiscovery.Target target(DatagramSocket socket, boolean ps5) {
        return new ConsoleDiscovery.Target((InetSocketAddress) socket.getLocalSocketAddress(), ps5);
    }
    private static DatagramPacket receive(DatagramSocket socket) throws Exception {
        DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
        socket.receive(packet);
        return packet;
    }
    private static String text(DatagramPacket packet) {
        return new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
    }
    private static void send(DatagramSocket socket, DatagramPacket request, String text) throws Exception {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(payload, payload.length, request.getSocketAddress()));
    }
    private static final class Recorder implements ConsoleDiscovery.Listener {
        final List<DiscoveredConsole> found = new CopyOnWriteArrayList<>();
        final CountDownLatch done = new CountDownLatch(1);
        volatile String error;
        volatile int completions;
        volatile Thread callbackThread;
        @Override public void onFound(DiscoveredConsole console) { callbackThread = Thread.currentThread(); found.add(console); }
        @Override public void onComplete() { completions++; done.countDown(); }
        @Override public void onError(String message) { error = message; done.countDown(); }
    }
}
