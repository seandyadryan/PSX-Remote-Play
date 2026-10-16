package com.deploydulupulangnanti.psxremoteplay.core;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Finite IPv4 LAN discovery; no registration, authentication, wake-up or subnet sweeps. */
public final class ConsoleDiscovery implements AutoCloseable {
    public static final int DEFAULT_DURATION_MILLIS = 15_000;
    private static final int RESEND_MILLIS = 250;
    private static final int MAX_CONSOLES = 128;
    private final Object lock = new Object();
    private Run current;

    /** Callbacks run on the discovery worker and must return promptly. Marshal Android UI work. */
    public interface Listener {
        void onFound(DiscoveredConsole console);
        void onComplete();
        void onError(String safeMessage);
    }

    /** The explicit console generation also permits test endpoints on unprivileged UDP ports. */
    public static final class Target {
        public final InetSocketAddress address;
        public final boolean ps5;
        public Target(InetSocketAddress address, boolean ps5) {
            this.address = Objects.requireNonNull(address);
            if (address.isUnresolved() || !(address.getAddress() instanceof java.net.Inet4Address)
                    || address.getPort() == 0) throw new IllegalArgumentException("Target harus alamat IPv4 dan port UDP.");
            this.ps5 = ps5;
        }
    }

    public void search(Listener listener) {
        begin(null, DEFAULT_DURATION_MILLIS, listener);
    }

    public void search(List<Target> targets, int durationMillis, Listener listener) {
        Objects.requireNonNull(targets);
        if (targets.isEmpty() || targets.size() > 128) throw new IllegalArgumentException("Target pencarian tidak valid.");
        List<Target> snapshot = new ArrayList<>(targets);
        for (Target target : snapshot) Objects.requireNonNull(target);
        begin(Collections.unmodifiableList(snapshot), durationMillis, listener);
    }

    private void begin(List<Target> targets, int durationMillis, Listener listener) {
        Objects.requireNonNull(listener);
        if (durationMillis < 1 || durationMillis > DEFAULT_DURATION_MILLIS) throw new IllegalArgumentException("Durasi pencarian 1–15000 ms.");
        synchronized (lock) {
            cancelLocked();
            Run run = new Run(listener, durationMillis);
            current = run;
            Thread worker = new Thread(() -> scan(run, targets), "console-discovery");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private void scan(Run run, List<Target> configuredTargets) {
        try {
            List<Target> targets = configuredTargets == null ? lanTargets() : configuredTargets;
            try (DatagramSocket socket = new DatagramSocket(0)) {
                synchronized (lock) {
                    if (current != run) return;
                    run.socket = socket;
                }
                socket.setBroadcast(true);
                byte[] buffer = new byte[DiscoveryProtocol.MAX_PACKET_BYTES + 1];
                Map<String, DiscoveredConsole> seen = new LinkedHashMap<>();
                long nextSend = 0;
                boolean sent = false;
                while (active(run) && System.nanoTime() < run.deadline) {
                    long now = System.nanoTime();
                    if (now >= nextSend) {
                        for (Target target : targets) {
                            if (!active(run) || System.nanoTime() >= run.deadline) break;
                            byte[] request = DiscoveryProtocol.searchRequest(target.ps5);
                            try {
                                socket.send(new DatagramPacket(request, request.length, target.address));
                                sent = true;
                            } catch (IOException ignored) { /* Other interfaces may still be reachable. */ }
                        }
                        if (!sent) { finish(run, "Pencarian tidak dapat mengirim paket. Periksa jaringan lokal."); return; }
                        nextSend = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(RESEND_MILLIS);
                    }
                    long remaining = Math.min(run.deadline, nextSend) - System.nanoTime();
                    if (remaining <= 0) continue;
                    socket.setSoTimeout((int) Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    try { socket.receive(packet); }
                    catch (SocketTimeoutException timeout) { continue; }
                    if (System.nanoTime() >= run.deadline || !active(run)) break;
                    DiscoveryProtocol.parse(packet).ifPresent(console -> {
                        DiscoveredConsole previous = seen.get(console.hostId);
                        if (previous == null && seen.size() >= MAX_CONSOLES) return;
                        if (previous == null || !console.sameAdvertisement(previous)) {
                            seen.put(console.hostId, console);
                            synchronized (lock) {
                                if (current == run) run.listener.onFound(console);
                            }
                        }
                    });
                }
                finish(run, null);
            }
        } catch (IOException | SecurityException error) {
            finish(run, "Pencarian jaringan gagal. Periksa koneksi dan izin jaringan.");
        } finally {
            synchronized (lock) { if (current == run) current = null; }
        }
    }

    private boolean active(Run run) { synchronized (lock) { return current == run; } }

    private void finish(Run run, String error) {
        synchronized (lock) {
            if (current != run) return;
            current = null;
            if (error == null) run.listener.onComplete(); else run.listener.onError(error);
        }
    }

    private static List<Target> lanTargets() throws SocketException {
        Set<InetAddress> broadcasts = new LinkedHashSet<>();
        try { broadcasts.add(InetAddress.getByAddress(new byte[]{-1, -1, -1, -1})); }
        catch (java.net.UnknownHostException impossible) { throw new AssertionError(impossible); }
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces != null) {
            while (interfaces.hasMoreElements() && broadcasts.size() < 64) {
                NetworkInterface item = interfaces.nextElement();
                if (!item.isUp() || item.isLoopback()) continue;
                for (InterfaceAddress address : item.getInterfaceAddresses()) {
                    if (address.getBroadcast() != null) broadcasts.add(address.getBroadcast());
                }
            }
        }
        List<Target> result = new ArrayList<>();
        for (InetAddress address : broadcasts) {
            result.add(new Target(new InetSocketAddress(address, DiscoveryProtocol.PS4_PORT), false));
            result.add(new Target(new InetSocketAddress(address, DiscoveryProtocol.PS5_PORT), true));
        }
        return result;
    }

    /** Cancels the current scan and unblocks receive. The instance can start another scan. */
    @Override public void close() { synchronized (lock) { cancelLocked(); } }

    private void cancelLocked() {
        Run previous = current;
        current = null;
        if (previous != null && previous.socket != null) previous.socket.close();
    }

    private static final class Run {
        final Listener listener;
        final long deadline;
        DatagramSocket socket;
        Run(Listener listener, int durationMillis) {
            this.listener = listener;
            this.deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(durationMillis);
        }
    }
}
