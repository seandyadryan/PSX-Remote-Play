package com.deploydulupulangnanti.psxremoteplay.core;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Objects;

/** Sends three finite wake-up probes. A sent packet does not confirm that a console woke up. */
public final class ConsoleWakeup implements AutoCloseable {
    private final Object lock = new Object();
    private Run current;

    /** Callbacks run on the worker; marshal UI updates to the Android main thread. */
    public interface Listener {
        void onSent();
        void onFailure(String safeMessage);
    }

    public void send(String host, ConsoleRegistration registration, Listener listener) {
        String validated = new Device("wakeup", "Konsol", host).host;
        begin(validated, null, registration, listener);
    }

    /** An explicitly resolved endpoint permits deterministic loopback transport tests. */
    public void send(InetSocketAddress endpoint, ConsoleRegistration registration, Listener listener) {
        Objects.requireNonNull(endpoint);
        if (endpoint.isUnresolved() || endpoint.getPort() == 0 || endpoint.getAddress().isAnyLocalAddress()
                || endpoint.getAddress().isMulticastAddress()) throw new IllegalArgumentException("Target UDP tidak valid.");
        begin(null, endpoint, registration, listener);
    }

    private void begin(String host, InetSocketAddress endpoint, ConsoleRegistration registration, Listener listener) {
        Objects.requireNonNull(listener);
        byte[] request = WakeupProtocol.request(registration);
        synchronized (lock) {
            cancelLocked();
            Run run = new Run(listener);
            current = run;
            Thread worker = new Thread(() -> transmit(run, host, endpoint, registration.ps5, request), "console-wakeup");
            worker.setDaemon(true);
            run.worker = worker;
            worker.start();
        }
    }

    private void transmit(Run run, String host, InetSocketAddress configured, boolean ps5, byte[] request) {
        try {
            InetSocketAddress endpoint = configured;
            if (endpoint == null) endpoint = new InetSocketAddress(InetAddress.getByName(host),
                    ps5 ? DiscoveryProtocol.PS5_PORT : DiscoveryProtocol.PS4_PORT);
            try (DatagramSocket socket = new DatagramSocket()) {
                synchronized (lock) {
                    if (current != run) return;
                    run.socket = socket;
                }
                for (int i = 0; i < 3; i++) {
                    synchronized (lock) {
                        if (current != run) return;
                        socket.send(new DatagramPacket(request, request.length, endpoint));
                    }
                    if (i < 2) Thread.sleep(250);
                }
                finish(run, null);
            }
        } catch (InterruptedException cancelled) {
            Thread.currentThread().interrupt();
        } catch (IOException | SecurityException error) {
            finish(run, "Paket wake-up gagal dikirim. Periksa alamat dan jaringan lokal.");
        } finally {
            synchronized (lock) { if (current == run) current = null; }
        }
    }

    private void finish(Run run, String error) {
        synchronized (lock) {
            if (current != run) return;
            current = null;
            if (error == null) run.listener.onSent(); else run.listener.onFailure(error);
        }
    }

    @Override public void close() { synchronized (lock) { cancelLocked(); } }

    private void cancelLocked() {
        Run previous = current;
        current = null;
        if (previous != null) {
            if (previous.socket != null) previous.socket.close();
            if (previous.worker != null) previous.worker.interrupt();
        }
    }

    private static final class Run {
        final Listener listener;
        DatagramSocket socket;
        Thread worker;
        Run(Listener listener) { this.listener = listener; }
    }
}
